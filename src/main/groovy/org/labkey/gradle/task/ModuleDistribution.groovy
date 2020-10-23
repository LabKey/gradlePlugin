/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.*
import org.labkey.gradle.plugin.extension.DistributionExtension
import org.labkey.gradle.plugin.extension.StagingExtension
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.PropertiesUtils

import java.nio.file.Files

class ModuleDistribution extends DefaultTask
{
    @Optional @Input
    Boolean includeZipArchive = false
    @Optional @Input
    Boolean includeTarGZArchive = false
    @Optional @Input
    Boolean includeWarArchive = false
    @Optional @Input
    Boolean makeDistribution = true // set to false for just an archive of modules
    @Optional @Input
    String extraFileIdentifier = ""
    @Optional @Input
    String versionPrefix = null
    @Optional @Input
    String subDirName
    @Optional @Input
    String archivePrefix = "LabKey"
    @Optional @Input
    String archiveName

    @OutputDirectory
    File distributionDir

    private final DistributionExtension distExtension

    ModuleDistribution()
    {
        description = "Make a LabKey modules distribution"
        distExtension = project.extensions.findByType(DistributionExtension.class)

        Project serverProject = BuildUtils.getServerProject(project)
        this.dependsOn(serverProject.tasks.setup)
        this.dependsOn(serverProject.tasks.stageApp)
    }

    @OutputDirectory
    File getResolvedDistributionDir()
    {
        if (distributionDir == null && subDirName != null)
            distributionDir = project.file("${distExtension.dir}/${subDirName}")
        return distributionDir
    }

    @OutputFiles
    List<File> getDistFiles()
    {
        List<File> distFiles = new ArrayList<>()

        if (includeTarGZArchive)
            distFiles.add(new File(getTarArchivePath()))
        if (includeZipArchive)
            distFiles.add(new File(getZipArchivePath()))
        if (makeDistribution)
        {
            distFiles.add(getDistributionFile())
            distFiles.add(getVersionFile())
        }
        return distFiles
    }

    @TaskAction
    void doAction()
    {
        if (makeDistribution)
            createDistributionFiles()
        gatherModules()
        packageRedistributables()

    }

    @OutputDirectory
    File getModulesDir()
    {
        // we use a common directory to save on disk space for TeamCity.  This mimics the behavior of the ant build.
        // (This is just a conjecture about why it continues to run out of space and not be able to copy files from one place to the other).
        return new File("${project.rootProject.buildDir}/distModules")
    }

    private void gatherModules()
    {
        File modulesDir = getModulesDir()
        modulesDir.deleteDir()
        project.copy
        { CopySpec copy ->
            copy.from { project.configurations.distribution }
            copy.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
            copy.into modulesDir
        }
    }

    private void packageRedistributables()
    {
        if (makeDistribution)
        {
            copyLibXml()
        }
        packageArchives()
    }

    private void copyLibXml()
    {
        Properties copyProps = new Properties()
        // Pre-configure labkey.xml to work with postgresql
        copyProps.put("jdbcURL", "jdbc:postgresql://localhost/labkey")
        copyProps.put("jdbcDriverClassName", "org.postgresql.Driver")

        project.copy
        { CopySpec copy ->
            copy.from("${project.rootProject.projectDir}/webapps")
            copy.include("labkey.xml")
            copy.into(project.buildDir)
            copy.filter({ String line ->
                return PropertiesUtils.replaceProps(line, copyProps, true)
            })
        }
    }

    private void packageArchives()
    {
        if (includeTarGZArchive)
        {
            tarArchives()
        }
        if (includeZipArchive)
        {
            zipArchives()
        }
        if (includeWarArchive)
        {
            warArchives()
        }
    }

    @Input
    String getArtifactId()
    {
        return subDirName
    }

    String getArchiveName()
    {
        if (archiveName == null)
        {
            archiveName = "${archivePrefix}${BuildUtils.getDistributionVersion(project)}${extraFileIdentifier}"
        }
        return archiveName
    }

    private String getTarArchivePath()
    {
        return "${getResolvedDistributionDir()}/${getArchiveName()}.tar.gz"
    }

    private String getZipArchivePath()
    {
        return "${getResolvedDistributionDir()}/${getArchiveName()}.zip"
    }

    private String getWarArchivePath()
    {
        return "${getResolvedDistributionDir()}/${getArchiveName()}.war"
    }

    private File getWindowsUtilDir()
    {
        return project.rootProject.file("external/windows/core")
    }

    private void copyWindowsCoreUtilities()
    {
        File utilsDir = getWindowsUtilDir()
        if (project.configurations.findByName("utilities") != null && !utilsDir.exists())
        {
            project.copy({
                CopySpec copy ->
                    copy.from(project.configurations.utilities.collect { project.zipTree(it) })
                    copy.into utilsDir
            })
        }
    }

    private void tarArchives()
    {
        if (makeDistribution)
        {
            copyWindowsCoreUtilities()
            def utilsDir = getWindowsUtilDir()
            StagingExtension staging = project.getExtensions().getByType(StagingExtension.class)

            ant.tar(tarfile: getTarArchivePath(),
                    longfile: "gnu",
                    compression: "gzip") {
                tarfileset(dir: staging.webappDir,
                        prefix: "${archiveName}/labkeywebapp") {
                    exclude(name: "WEB-INF/classes/distribution")
                }
                tarfileset(dir: getModulesDir(),
                        prefix: "${archiveName}/modules") {
                    include(name: "*.module")
                }
                tarfileset(dir: staging.tomcatLibDir, prefix: "${archiveName}/tomcat-lib") {
                    // this exclusion is necessary because for some reason when buildFromSource=false,
                    // the tomcat bootstrap jar is included in the staged libraries and the LabKey bootstrap jar is not.
                    // Not sure why.
                    exclude(name: "bootstrap.jar")
                }

                tarfileset(dir: utilsDir.path, prefix: "${archiveName}/bin")

                tarfileset(dir: staging.pipelineLibDir, prefix: "${archiveName}/pipeline-lib")

                tarfileset(dir: "${project.buildDir}/",
                        prefix: archiveName,
                        mode: 744) {
                    include(name: "manual-upgrade.sh")
                }

                tarfileset(dir: project.buildDir,
                        prefix: archiveName) {
                    include(name: "README.txt")
                    include(name: "VERSION")
                    include(name: "labkeywebapp/**")
                    include(name: "nlp/**")
                    include(name: "labkey.xml")
                }
            }
        }
        else
        {
            ant.tar(tarfile: getTarArchivePath(),
                    longfile: "gnu",
                    compression: "gzip") {
                tarfileset(dir: getModulesDir(),
                        prefix: "${archiveName}/modules") {
                    include(name: "*.module")
                }
            }
        }

    }

    private void zipArchives()
    {
        if (makeDistribution)
        {
            copyWindowsCoreUtilities();
            def utilsDir = getWindowsUtilDir()
            StagingExtension staging = project.getExtensions().getByType(StagingExtension.class)

            ant.zip(destfile: getZipArchivePath()) {
                zipfileset(dir: staging.webappDir,
                        prefix: "${archiveName}/labkeywebapp") {
                    exclude(name: "WEB-INF/classes/distribution")
                }
                zipfileset(dir: getModulesDir(),
                        prefix: "${archiveName}/modules") {
                    include(name: "*.module")
                }
                zipfileset(dir: staging.tomcatLibDir, prefix: "${archiveName}/tomcat-lib") {
                    // this exclusion is necessary because for some reason when buildFromSource=false,
                    // the tomcat bootstrap jar is included in the staged libraries and the LabKey bootstrap jar is not.
                    // Not sure why.
                    exclude(name: "bootstrap.jar")
                }

                zipfileset(dir: utilsDir.path, prefix: "${archiveName}/bin")

                zipfileset(dir: staging.pipelineLibDir, prefix: "${archiveName}/pipeline-lib")

                zipfileset(dir: "${project.buildDir}/",
                        prefix: "${archiveName}",
                        filemode: 744){
                    include(name: "manual-upgrade.sh")
                }

                zipfileset(dir: "${project.buildDir}/",
                        prefix: "${archiveName}") {
                    include(name: "README.txt")
                    include(name: "VERSION")
                    include(name: "labkeywebapp/**")
                    include(name: "nlp/**")
                    include(name: "labkey.xml")
                }
            }
        }
        else
        {
            ant.zip(destfile: getZipArchivePath()) {
                zipfileset(dir: getModulesDir(),
                        prefix: "${archiveName}/modules") {
                    include(name: "*.module")
                }
            }
        }
    }

    // CONSIDER sort module by module dependencies
    //   I don't think this is a problem with core labkey modules today, but it could be
    private void warArchives()
    {
        StagingExtension staging = project.getExtensions().getByType(StagingExtension.class)
        String warArchivePath = getWarArchivePath()

        String[] modules = getModulesDir().list { File dir, String name -> name.endsWith(".module") }

        // explode the modules before trying to assemble the .war
        List<File> explodedModules = new ArrayList<>()
        Arrays.asList(modules).forEach({ String moduleFile ->
            String moduleName = moduleFile.substring(0, moduleFile.size() - ".module".size())
            File explodedDir = new File(new File(warArchivePath).parentFile, moduleName)
            explodedModules.push(explodedDir)
            ant.unzip(src: new File(getModulesDir(),moduleFile), dest: explodedDir)
        })

        ant.zip(destfile: warArchivePath, duplicate:preserve) {

            zipfileset(dir: staging.webappDir,
                    prefix: "") {
                exclude(name: "WEB-INF/classes/distribution")
            }

            // TODO mail.jar should be picked up from api module (like jdbc jars)
            zipfileset(dir: staging.tomcatLibDir, prefix: "WEB-INF/lib") {
                include(name: "mail.jar")
            }

            // NOTE: if we want to enable running w/o LabKeyBootstrapClassLoader we have to do some of its work here (see ExplodedModule)
            // NOTE: in particular some files need to be moved out of the module and into the WEB-INF directory

            explodedModules.forEach({ File moduleDir ->
                String moduleName = moduleDir.getName()

                // copy the modules files that stay in the exploded module directory
                zipfileset(dir: moduleDir,
                        prefix: "WEB-INF/modules/" + moduleName) {
                    exclude(name: "lib/**/*.jar")
                    exclude(name: "web/**/*.gwt.rpc")
                    exclude(name: "**/*Context.xml")
                }

                // WEB-INF (web.xml, labkey.tld)
                zipfileset(dir: new File(moduleDir, "web/WEB-INF"),
                        prefix: "WEB-INF/",
                        erroronmissingdir: false) {
                    include(name: "*")
                }

                // WEB-INF/lib (*.jar)
                zipfileset(dir: moduleDir,
                        prefix: "WEB-INF",
                        erroronmissingdir: false) {
                    include(name: "lib/*.jar")
                }

                // gwt.rpc
                zipfileset(dir: new File(moduleDir, "web"),
                        prefix: "",
                        erroronmissingdir: false) {
                    include(name: "**/*.gwt.rpc")
                }

                // spring Context.xml files
                zipfileset(dir: new File(moduleDir, "config"),
                        prefix: "WEB-INF/",
                        erroronmissingdir: false) {
                    include(name: "**/*Context.xml")
                }
            })
        }

        explodedModules.forEach({ File moduleDir -> moduleDir.deleteDir() })
    }


    private void createDistributionFiles()
    {
        writeDistributionFile()
        writeVersionFile()
        // This seems a very convoluted way to get to the zip file in the jar file.  Using the classLoader did not
        // work as expected, however.  Following the example from here:
        // https://discuss.gradle.org/t/gradle-plugin-copy-directory-tree-with-files-from-resources/12767/7
        FileTree jarTree = project.zipTree(getClass().getProtectionDomain().getCodeSource().getLocation().toExternalForm())
        File zipFile = jarTree.matching({
            include "distributionResources.zip"
        }).singleFile
        project.copy({ CopySpec copy ->
            copy.from(project.zipTree(zipFile))
            copy.into(project.buildDir)
        })
        // This is necessary for reasons that are unclear.  Without it, you get:
        // -bash: ./manual-upgrade.sh: /bin/sh^M: bad interpreter: No such file or directory
        // even though the original file has unix line endings. Dunno.
        project.ant.fixcrlf (srcdir: project.buildDir, includes: "manual-upgrade.sh", eol: "unix")
    }

    private File getDistributionFile()
    {
        File distExtraDir = new File(project.buildDir, DistributionExtension.DIST_FILE_DIR)
        return new File(distExtraDir,  DistributionExtension.DIST_FILE_NAME)
    }

    private void writeDistributionFile()
    {
        Files.write(getDistributionFile().toPath(), project.name.getBytes())
    }

    @OutputFile
    File getVersionFile()
    {
        return new File(project.buildDir.absolutePath, DistributionExtension.VERSION_FILE_NAME)
    }

    private void writeVersionFile()
    {
        Files.write(getVersionFile().toPath(), ((String) project.version).getBytes())
    }
}
