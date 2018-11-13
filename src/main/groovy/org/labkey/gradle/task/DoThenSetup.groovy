/*
 * Copyright (c) 2016-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.util.DatabaseProperties
import org.labkey.gradle.util.PropertiesUtils

class DoThenSetup extends DefaultTask
{
    protected DatabaseProperties databaseProperties
    boolean dbPropertiesChanged = false

    Closure<Void> fn = {
        setDatabaseProperties()
    }

    DoThenSetup()
    {
        if (project.findProject(":server") != null)
            this.dependsOn project.project(":server").configurations.tomcatJars
    }

    // Currently this type of task will always run because there are no declared inputs and outputs
    // The following methods are probably close to the right things, but need to be tested thoroughly
//    @InputFiles
//    FileCollection getInputFiles() {
//        FileCollection files = project.project(":server").configurations.tomcatJars
//        return files.add(project.files(new File("${project.rootProject.projectDir}/webapps/labkey.xml")))
//    }
//
//    @OutputFiles
//    List<File> getOutputFiles() {
//        List<File> files = new ArrayList<>();
//        for (String jar : ServerDeploy.TOMCAT_LIB_UNVERSIONED_JARS) {
//            files.add(new File(project.staging.tomcatLibDir, jar))
//            files.add(new File("${project.tomcatDir}/lib/${jar}"))
//        }
//        files.add(new File("${project.rootProject.buildDir}/labkey.xml"))
//        files.add(new File("${project.ext.tomcatConfDir}/labkey.xml"))
//        return files
//    }

    private boolean canCreate(File file)
    {
        file = file.getParentFile()

        while (file != null)
        {
            if (file.exists())
            {
                return file.canWrite() && file.canRead()
            }
            file = file.getParentFile()
        }
        return false
    }

    @TaskAction
    void setup() {
        File tomcatConfDir = project.file(project.ext.tomcatConfDir)
        if (tomcatConfDir.exists())
        {
            if (!tomcatConfDir.isDirectory())
                throw new GradleException("No such directory: ${tomcatConfDir.absolutePath}")
            if (!tomcatConfDir.canWrite() || !tomcatConfDir.canRead())
                throw new GradleException("Directory ${tomcatConfDir.absolutePath} does not have proper permissions")
        }
        else if (!canCreate(tomcatConfDir))
            throw new GradleException("Insufficient permissions to create ${tomcatConfDir.absolutePath}")

        getFn().run()

        String appDocBase = project.serverDeploy.webappDir.toString().split("[/\\\\]").join("${File.separator}")

        if (!labkeyXmlUpToDate(appDocBase))
        {
            Properties configProperties = databaseProperties.getConfigProperties()
            configProperties.setProperty("appDocBase", appDocBase)
            boolean isNextLineComment = false

            project.copy({ CopySpec copy ->
                copy.from "${project.rootProject.projectDir}/webapps"
                copy.into "${project.rootProject.buildDir}"
                copy.include "labkey.xml"
                copy.filter({ String line ->
                    String newLine = line;

                    if (project.ext.has('enableJms') && project.ext.enableJms)
                    {
                        newLine = newLine.replace("<!--@@jmsConfig@@", "");
                        newLine = newLine.replace("@@jmsConfig@@-->", "");
                        return newLine;
                    }
                    // If we want to automatically enable an LDAP Sync that is hardcoded in the labkey.xml
                    // for testing purposes, this will uncomment that stanza if the enableLdapSync
                    // property is defined.
                    if (project.hasProperty('enableLdapSync'))
                    {
                        newLine = newLine.replace("<!--@@ldapSyncConfig@@", "");
                        newLine = newLine.replace("@@ldapSyncConfig@@-->", "");
                        return newLine;
                    }
                    if (isNextLineComment || newLine.contains("<!--"))
                    {
                        isNextLineComment = !newLine.contains("-->");
                        return newLine;
                    }
                    return PropertiesUtils.replaceProps(line, configProperties, true);
                })
            })

            project.copy({ CopySpec copy ->
                copy.from "${project.rootProject.buildDir}"
                copy.into "${project.ext.tomcatConfDir}"
                copy.include "labkey.xml"
            })
        }

        if (project.findProject(":server") != null)
            copyTomcatJars()


    }


    // labkeyXml is up to date if it was created after the current config file was created
    // and it has the current appDocBase
    boolean labkeyXmlUpToDate(String appDocBase)
    {
        if (this.dbPropertiesChanged)
            return false;

        File dbPropFile = DatabaseProperties.getPickedConfigFile(project)
        File tomcatLabkeyXml = new File("${project.ext.tomcatConfDir}", "labkey.xml")
        if (!dbPropFile.exists() || !tomcatLabkeyXml.exists())
            return false
        if (dbPropFile.lastModified() < tomcatLabkeyXml.lastModified())
        {
            // make sure we haven't switch contexts
            for (String line: tomcatLabkeyXml.readLines())
            {
                if (line.contains("docBase=\"" + appDocBase + "\""))
                    return true
            }
        }
        return false
    }

    private void copyTomcatJars()
    {
        Project serverProject = project.project(":server")
        // Remove the staging tomcatLib directory before copying into it to avoid duplicates.
        project.delete project.staging.tomcatLibDir
        // for consistency with a distribution deployment and the treatment of all other deployment artifacts,
        // first copy the tomcat jars into the staging directory
        project.ant.copy(

                todir: project.staging.tomcatLibDir,
                preserveLastModified: true,
                overwrite: true // Issue 33473: overwrite the existing jars to facilitate switching to older versions of labkey with older dependencies
        )
            {
                serverProject.configurations.tomcatJars { Configuration collection ->
                    collection.addToAntBuilder(project.ant, "fileset", FileCollection.AntType.FileSet)
                }
                // Put unversioned files into the tomcatLibDir.  These files are meant to be copied into
                // the tomcat/lib directory when deploying a build or a distribution.  When version numbers change,
                // you will end up with multiple versions of these jar files on the classpath, which will often
                // result in problems of compatibility.  Additionally, we want to maintain the (incorrect) names
                // of the files that have been used with the Ant build process.
                //
                // We may employ CATALINA_BASE in order to separate our libraries from the ones that come with
                // the tomcat distribution. This will require updating our instructions for installation by clients
                // but would allow us to use artifacts with more self-documenting names.
                chainedmapper()
                        {
                            flattenmapper()
                            // get rid of the version numbers on the jar files
                            // matches on: name-X.Y.Z-SNAPSHOT.jar, name-X.Y.Z_branch-SNAPSHOT.jar, name-X.Y.Z.jar
                            //
                            // N.B.  Attempts to use BuildUtils.VERSIONED_ARTIFACT_NAME_PATTERN here fail for the javax.mail-X.Y.Z.jar file.
                            // The Ant regexpmapper chooses only javax as \\1, which is not what is wanted
                            regexpmapper(from: "^(.*?)(-\\d+(\\.\\d+)*(_.+)?(-SNAPSHOT)?)?\\.jar", to: "\\1.jar")
                            filtermapper()
                                    {
                                        replacestring(from: "mysql-connector-java", to: "mysql") // the Ant build used mysql.jar
                                        replacestring(from: "javax.mail", to: "mail") // the Ant build used mail.jar
                                    }
                        }
            }

        // Then copy them into the tomcat/lib directory
        project.ant.copy(
                todir: "${project.tomcatDir}/lib",
                preserveLastModified: true,
                overwrite: true
        )
        {
            fileset(dir: project.staging.tomcatLibDir)
        }
    }

    protected void setDatabaseProperties()
    {
        databaseProperties = new DatabaseProperties(project, false)
    }

    void setDatabaseProperties(DatabaseProperties dbProperties)
    {
        this.databaseProperties = dbProperties
    }

    DatabaseProperties getDatabaseProperties()
    {
        return databaseProperties
    }
}
