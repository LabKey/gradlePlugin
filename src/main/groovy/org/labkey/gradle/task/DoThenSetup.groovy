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
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.ServerDeploy
import org.labkey.gradle.plugin.extension.TeamCityExtension
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.DatabaseProperties
import org.labkey.gradle.util.PropertiesUtils


class DoThenSetup extends DefaultTask
{
    @Optional @Input
    protected DatabaseProperties databaseProperties
    @Input
    boolean dbPropertiesChanged = false

    DoThenSetup()
    {
        Project serverProject = BuildUtils.getServerProject(project)
        if (serverProject != null)
            this.dependsOn serverProject.configurations.tomcatJars
    }

    private static boolean canCreate(File file)
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

    protected void doDatabaseTask()
    {
        setDatabaseProperties()
    }

    @TaskAction
    void setup() {
        doDatabaseTask()
        boolean useEmbeddedTomcat = BuildUtils.useEmbeddedTomcat(project)
        if (!useEmbeddedTomcat)
        {
            project.tomcat.validateCatalinaHome()
            File tomcatConfDir = project.file(project.tomcat.tomcatConfDir)
            if (tomcatConfDir.exists()) {
                if (!tomcatConfDir.isDirectory())
                    throw new GradleException("No such directory: ${tomcatConfDir.absolutePath}")
                if (!tomcatConfDir.canWrite() || !tomcatConfDir.canRead())
                    throw new GradleException("Directory ${tomcatConfDir.absolutePath} does not have proper permissions")
            } else if (!canCreate(tomcatConfDir))
                throw new GradleException("Insufficient permissions to create ${tomcatConfDir.absolutePath}")

            String appDocBase = project.serverDeploy.webappDir.toString().split("[/\\\\]").join("${File.separator}")

            if (!labkeyXmlUpToDate(appDocBase)) {
                Properties configProperties = databaseProperties.getConfigProperties()
                configProperties.putAll(getExtraJdbcProperties())
                configProperties.setProperty("appDocBase", appDocBase)
                boolean isNextLineComment = false
                FileTree webappsDir = BuildUtils.getWebappConfigPath(project)
                project.copy({ CopySpec copy ->
                    copy.from webappsDir
                    copy.into "${project.rootProject.buildDir}"
                    copy.include "labkey.xml"
                    copy.filter({ String line ->
                        if (project.ext.has('enableJms') && project.ext.enableJms) {
                            line = line.replace("<!--@@jmsConfig@@", "")
                            line = line.replace("@@jmsConfig@@-->", "")
                            return line
                        }
                        // If we want to automatically enable an LDAP Sync that is hardcoded in the labkey.xml
                        // for testing purposes, this will uncomment that stanza if the enableLdapSync
                        // property is defined.
                        if (project.hasProperty('enableLdapSync')) {
                            line = line.replace("<!--@@ldapSyncConfig@@", "")
                            line = line.replace("@@ldapSyncConfig@@-->", "")
                            return line
                        }
                        if (project.hasProperty("extraJdbcDataSource"))
                        {
                            line = line.replace("<!--@@extraJdbcDataSource@@", "")
                            line = line.replace("@@extraJdbcDataSource@@-->", "")
                        }
                        if (isNextLineComment || line.contains("<!--")) {
                            isNextLineComment = !line.contains("-->")
                            return line // Don't apply replacements to comments
                        }
                        return PropertiesUtils.replaceProps(line, configProperties, true)
                    })
                })

                project.copy({ CopySpec copy ->
                    copy.from "${project.rootProject.buildDir}"
                    copy.into "${project.tomcat.tomcatConfDir}"
                    copy.include "labkey.xml"
                })
            }
        }
        else {
            if (!embeddedConfigUpToDate()) {
                Properties configProperties = databaseProperties.getConfigProperties()
                configProperties.putAll(getExtraJdbcProperties())
                if (project.hasProperty("useLocalBuild"))
                    // in .properties files, backward slashes are seen as escape characters, so all paths must use forward slashes, even on Windows
                    configProperties.setProperty("pathToServer", project.rootDir.getAbsolutePath().replaceAll("\\\\", "/"))
                if (TeamCityExtension.getLabKeyServerPort(project) != null)
                    configProperties.setProperty("serverPort", TeamCityExtension.getLabKeyServerPort(project))
                else if (project.hasProperty("serverPort"))
                    configProperties.setProperty("serverPort", (String) project.property("serverPort"))
                else if (project.hasProperty("useSsl"))
                    configProperties.setProperty("serverPort", "8443")
                else
                    configProperties.setProperty("serverPort", "8080")
                String embeddedDir = BuildUtils.getEmbeddedConfigPath(project)
                File configsDir = new File(BuildUtils.getConfigsProject(project).projectDir, "configs")
                project.copy({ CopySpec copy ->
                    copy.from configsDir
                    copy.into embeddedDir
                    copy.include "application.properties"
                    copy.filter({ String line ->
                        if (project.hasProperty("useSsl")) {
                            line = line.replace("#server.ssl", "server.ssl")
                        }
                        if (project.hasProperty("useLocalBuild")) {
                            line = line.replace("#context.webAppLocation=", "context.webAppLocation=")
                            line = line.replace("#spring.devtools.restart.additional-paths=", "spring.devtools.restart.additional-paths=")
                        }
                        if (databaseProperties.hasProperty("extraJdbcDataSource"))
                        {
                            line = line.replaceAll("^#(context\\..+\\[1].*)", "\$1")
                        }
                        if (line.startsWith("#")) {
                            return line // Don't apply replacements to comments
                        }
                        return PropertiesUtils.replaceProps(line, configProperties, false)
                    })
                })
            }
        }
        if (BuildUtils.getServerProject(project) != null)
            copyTomcatJars()

    }

    private Properties getExtraJdbcProperties()
    {
        def extraJdbcProperties = new Properties();
        def tcProperties = TeamCityExtension.getTeamCityProperties(project)
        for (Map.Entry entry : tcProperties.entrySet())
        {
            if (entry.getKey().startsWith("extraJdbc"))
            {
                extraJdbcProperties.put(entry.getKey(), entry.getValue())
            }
        }
        return extraJdbcProperties
    }


    // labkeyXml is up to date if it was created after the current config file was created
    // and it has the current appDocBase
    boolean labkeyXmlUpToDate(String appDocBase)
    {
        if (this.dbPropertiesChanged)
            return false

        File dbPropFile = DatabaseProperties.getPickedConfigFile(project)
        File tomcatLabkeyXml = new File("${project.tomcat.tomcatConfDir}", "labkey.xml")
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
        Project serverProject = BuildUtils.getServerProject(project)
        // Remove the staging tomcatLib directory before copying into it to avoid duplicates.
        project.delete project.staging.tomcatLibDir
        // set debug logging for ant to see what's going wrong with the pickMssql task on Windows
        ant.project.buildListeners[0].messageOutputLevel = 4
        // for consistency with a distribution deployment and the treatment of all other deployment artifacts,
        // first copy the tomcat jars into the staging directory

        // We resolve the tomcatJars files outside of the ant copy because this seems to avoid
        // an error we saw on TeamCity when running the pickMssql task on Windows when updating to Gradle 6.7
        // The error in the gradle log was:
        //       org.apache.tools.ant.BuildException: copy doesn't support the nested "exec" element.
        // Theory is that when the files in the configuration have not been resolved, they get resolved
        // inside the node being added to the ant task below and that is not supported.
        Set<File> tomcatFiles = serverProject.configurations.tomcatJars.files
        this.logger.info("Copying to ${project.staging.tomcatLibDir}")
        this.logger.info("tomcatFiles are ${tomcatFiles}")
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
                                        replacestring(from: "jakarta.mail", to: "mail") // the Ant build used mail.jar
                                        replacestring(from: "jakarta.activation", to: "javax.activation") // the Ant build used javax.activation.jar
                                    }
                        }
            }

        ServerDeploy.JDBC_JARS.each{String name -> new File("${project.tomcat.catalinaHome}/lib/${name}").delete()}

        // Then copy them into the tomcat/lib directory
        this.logger.info("Copying files from ${project.staging.tomcatLibDir} to ${project.tomcat.catalinaHome}/lib")
        project.ant.copy(
                todir: "${project.tomcat.catalinaHome}/lib",
                preserveLastModified: true,
                overwrite: true
        )
        {
            fileset(dir: project.staging.tomcatLibDir)
        }
    }

    boolean embeddedConfigUpToDate()
    {
        if (this.dbPropertiesChanged)
            return false

        File dbPropFile = DatabaseProperties.getPickedConfigFile(project)
        File applicationPropsFile = new File(BuildUtils.getEmbeddedConfigPath(project), "application.properties")
        if (!dbPropFile.exists() || !applicationPropsFile.exists())
            return false
        if (dbPropFile.lastModified() < applicationPropsFile.lastModified())
        {
            return true
        }
        return false
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
