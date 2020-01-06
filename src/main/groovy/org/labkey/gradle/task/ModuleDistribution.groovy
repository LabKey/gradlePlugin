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
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
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
    Boolean includeMassSpecBinaries = false
    @Optional @Input
    String versionPrefix = null
    @Optional @Input
    String subDirName
    @Optional @Input
    String artifactName

    @OutputDirectory
    File distributionDir

    @Optional @Input
    String archivePrefix

    private DistributionExtension distExtension

    ModuleDistribution()
    {
        description = "Make a LabKey modules distribution"
        distExtension = project.extensions.findByType(DistributionExtension.class)

        this.dependsOn(project.project(":server").tasks.setup)
        this.dependsOn(project.project(":server").tasks.stageApp)
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

    private String getResolvedVersionPrefix()
    {
        if (versionPrefix == null)
            versionPrefix = "LabKey${BuildUtils.getDistributionVersion(project)}${extraFileIdentifier}"
        return versionPrefix
    }

    private String getResolvedArchivePrefix()
    {
        if (archivePrefix == null)
            archivePrefix = "${getResolvedVersionPrefix()}-bin"
        return archivePrefix
    }

    @OutputDirectory
    File getModulesDir()
    {
        // we use a common directory to save on disk space for TeamCity.  This mimics the behavior of the ant build.
        // (This is just a conjecture about why it continues to run out of space and not be able to copy files from one place to the other).
        return new File("${project.rootProject.buildDir}/distModules")
//        return new File(project.buildDir, "modules")
    }

    private void gatherModules()
    {
        File modulesDir = getModulesDir()
        modulesDir.deleteDir()
        project.copy
        { CopySpec copy ->
            copy.from { project.configurations.distribution }
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

    String getArtifactName()
    {
        if (artifactName == null)
        {
            if (makeDistribution)
                artifactName = getResolvedArchivePrefix()
            else
                artifactName = getResolvedVersionPrefix()
        }
        return artifactName
    }

    private String getTarArchivePath()
    {
        return "${getResolvedDistributionDir()}/${getArtifactName()}.tar.gz"
    }

    private String getZipArchivePath()
    {
        return "${getResolvedDistributionDir()}/${getArtifactName()}.zip"
    }

    private String getWarArchivePath()
    {
        return "${getResolvedDistributionDir()}/${getArtifactName()}.war"
    }

    private void tarArchives()
    {
        String archivePrefix = getResolvedArchivePrefix()
        if (makeDistribution)
        {
            StagingExtension staging = project.getExtensions().getByType(StagingExtension.class)

            ant.tar(tarfile: getTarArchivePath(),
                    longfile: "gnu",
                    compression: "gzip") {
                tarfileset(dir: staging.webappDir,
                        prefix: "${archivePrefix}/labkeywebapp") {
                    exclude(name: "WEB-INF/classes/distribution")
                }
                tarfileset(dir: getModulesDir(),
                        prefix: "${archivePrefix}/modules") {
                    include(name: "*.module")
                }
                tarfileset(dir: staging.tomcatLibDir, prefix: "${archivePrefix}/tomcat-lib") {
                    // this exclusion is necessary because for some reason when buildFromSource=false,
                    // the tomcat bootstrap jar is included in the staged libraries and the LabKey bootstrap jar is not.
                    // Not sure why.
                    exclude(name: "bootstrap.jar")
                }

                tarfileset(dir: "${project.rootProject.projectDir}/external/windows/",
                        prefix: "${archivePrefix}/bin") {
                    exclude(name: "**/.svn")
                    include(name: "core/**/*")
                    if (includeMassSpecBinaries)
                    {
                        include(name: "tpp/**/*")
                        include(name: "comet/**/*")
                        include(name: "msinspect/**/*")
                        include(name: "labkey/**/*")
                        include(name: "pwiz/**/*")
                    }
                }

                tarfileset(dir: staging.pipelineLibDir,
                        prefix: "${archivePrefix}/pipeline-lib")

                tarfileset(dir: "${project.buildDir}/",
                        prefix: archivePrefix,
                        mode: 744) {
                    include(name: "manual-upgrade.sh")
                }

                tarfileset(dir: project.buildDir,
                        prefix: archivePrefix) {
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
                        prefix: "${archivePrefix}/modules") {
                    include(name: "*.module")
                }
            }
        }

    }

    private void zipArchives()
    {
        String archivePrefix = this.getResolvedArchivePrefix()
        if (makeDistribution)
        {
            StagingExtension staging = project.getExtensions().getByType(StagingExtension.class)

            ant.zip(destfile: getZipArchivePath()) {
                zipfileset(dir: staging.webappDir,
                        prefix: "${archivePrefix}/labkeywebapp") {
                    exclude(name: "WEB-INF/classes/distribution")
                }
                zipfileset(dir: getModulesDir(),
                        prefix: "${archivePrefix}/modules") {
                    include(name: "*.module")
                }
                zipfileset(dir: staging.tomcatLibDir, prefix: "${archivePrefix}/tomcat-lib") {
                    // this exclusion is necessary because for some reason when buildFromSource=false,
                    // the tomcat bootstrap jar is included in the staged libraries and the LabKey bootstrap jar is not.
                    // Not sure why.
                    exclude(name: "bootstrap.jar")
                }

                zipfileset(dir: "${project.rootProject.projectDir}/external/windows/",
                        prefix: "${archivePrefix}/bin") {
                    exclude(name: "**/.svn")
                    include(name: "core/**/*")
                    if (includeMassSpecBinaries)
                    {
                        include(name: "tpp/**/*")
                        include(name: "comet/**/*")
                        include(name: "msinspect/**/*")
                        include(name: "labkey/**/*")
                        include(name: "pwiz/**/*")
                    }
                }

                zipfileset(dir: staging.pipelineLibDir,
                        prefix: "${archivePrefix}/pipeline-lib")

                zipfileset(dir: "${project.buildDir}/",
                        prefix: "${archivePrefix}",
                        filemode: 744){
                    include(name: "manual-upgrade.sh")
                }

                zipfileset(dir: "${project.buildDir}/",
                        prefix: "${archivePrefix}") {
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
                        prefix: "${archivePrefix}/modules") {
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

        ant.zip(destfile: warArchivePath) {

            zipfileset(dir: staging.webappDir,
                    prefix: "") {
                exclude(name: "WEB-INF/classes/distribution")
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
                zipfileset(dir: moduleDir,
                        prefix: "WEB-INF/modules/" + moduleName) {
                    include(name: "lib/*_jsp*.jar")
                }

                // WEB-INF (web.xml, labkey.tld)
                zipfileset(dir: new File(moduleDir, "web/WEB-INF"),
                        prefix: "WEB-INF/",
                        erroronmissingdir: false) {
                    include(name: "*")
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
