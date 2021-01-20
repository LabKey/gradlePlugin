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
import org.labkey.gradle.util.PomFileHelper
import org.labkey.gradle.util.PropertiesUtils

import java.nio.file.Files

class ModuleDistribution extends DefaultTask
{
    @Optional @Input
    Boolean includeZipArchive = false
    @Optional @Input
    Boolean includeTarGZArchive = false
    @Optional @Input
    String embeddedArchiveType = null
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
    @Optional @Input
    Boolean isOpenSource = false

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
        if (BuildUtils.useEmbeddedTomcat(project))
            this.dependsOn(project.project(BuildUtils.getEmbeddedProjectPath()).tasks.build)

        project.apply plugin: 'org.labkey.build.base'
        PomFileHelper.LABKEY_ORG_URL
    }

    @OutputDirectory
    File getResolvedDistributionDir()
    {
        if (distributionDir == null && subDirName != null)
            distributionDir = project.file("${distExtension.dir}/${subDirName}")
        return distributionDir
    }

    private boolean shouldBuildEmbeddedArchive(String extension = null) {
        return (embeddedArchiveType != null && (extension == null || embeddedArchiveType.indexOf(extension) >= 0)) &&
                makeDistribution && BuildUtils.useEmbeddedTomcat(project)
    }

    @OutputFiles
    List<File> getDistFiles()
    {
        List<File> distFiles = new ArrayList<>()

        if (shouldBuildEmbeddedArchive())
            distFiles.add(new File(getEmbeddedTomcatJarPath()))

        if (includeTarGZArchive)
            distFiles.add(new File(getTarArchivePath()))
        if (shouldBuildEmbeddedArchive(DistributionExtension.TAR_ARCHIVE_EXTENSION))
            distFiles.add(new File(getEmbeddedTarArchivePath()))
        if (includeZipArchive)
            distFiles.add(new File(getZipArchivePath()))
        if (shouldBuildEmbeddedArchive(DistributionExtension.ZIP_ARCHIVE_EXTENSION))
            distFiles.add(new File(getEmbeddedZipArchivePath()))

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
        project.copy {
            CopySpec copy ->
                copy.from { project.configurations.distribution }
                copy.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
                copy.into modulesDir
        }
        if (!isOpenSource)
        {
            this.logger.quiet("Copying patched api module")
            project.copy {
                CopySpec copy ->
                    copy.from(project.configurations.extJsCommercial)
                    copy.rename { String fileName ->
                        fileName.replace("-extJsCommercial", "")
                    }
                    copy.into modulesDir
                    copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
            }
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
            tarArchives()
        if (shouldBuildEmbeddedArchive(DistributionExtension.TAR_ARCHIVE_EXTENSION))
            embeddedTomcatTarArchive()
        if (includeZipArchive)
            zipArchives()
        if (shouldBuildEmbeddedArchive(DistributionExtension.ZIP_ARCHIVE_EXTENSION))
            embeddedTomcatZipArchive()
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

    private String getEmbeddedTomcatJarPath()
    {
        return "${project.buildDir}/labkeyServer-${project.version}.jar"
    }

    private String getTarArchivePath()
    {
        return "${getResolvedDistributionDir()}/${getArchiveName()}.${DistributionExtension.TAR_ARCHIVE_EXTENSION}"
    }

    private String getEmbeddedTarArchivePath()
    {
        return "${getResolvedDistributionDir()}/${getArchiveName()}${DistributionExtension.EMBEDDED_SUFFIX}.${DistributionExtension.TAR_ARCHIVE_EXTENSION}"
    }

    private String getZipArchivePath()
    {
        return "${getResolvedDistributionDir()}/${getArchiveName()}.${DistributionExtension.ZIP_ARCHIVE_EXTENSION}"
    }

    private String getEmbeddedZipArchivePath()
    {
        return "${getResolvedDistributionDir()}/${getArchiveName()}${DistributionExtension.EMBEDDED_SUFFIX}.${DistributionExtension.ZIP_ARCHIVE_EXTENSION}"
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

    private makeEmbeddedTomcatJar()
    {
        StagingExtension staging = project.getExtensions().getByType(StagingExtension.class)

        File embeddedJarFile = project.project(BuildUtils.getEmbeddedProjectPath(project.gradle)).tasks.jar.outputs.files.singleFile
        File modulesZipFile = new File(project.buildDir, "labkey/distribution.zip")
        File serverJarFile = new File(getEmbeddedTomcatJarPath())
        ant.zip(destFile: modulesZipFile.getAbsolutePath()) {
            zipfileset(dir: staging.webappDir,
                    prefix: "labkeywebapp") {
                exclude(name: "WEB-INF/classes/distribution")
            }
            zipfileset(dir: new File("${project.rootProject.buildDir}/distModules"),
                    prefix: "modules") {
                include(name: "*.module")
            }
            zipfileset(dir: "${project.buildDir}/") {
                include(name: "labkeywebapp/**")
            }
        }

        project.copy {
            CopySpec copy ->
                copy.from(embeddedJarFile)
                copy.into(project.buildDir)
                copy.rename(embeddedJarFile.getName(), serverJarFile.getName())
        }

        ant.jar(
            destfile: new File(project.buildDir, serverJarFile.getName()),
            update: true,
            keepcompression: true
        ) {
            fileset(dir: "${project.buildDir}", includes: "labkey/**")
        }
    }

    private void embeddedTomcatTarArchive()
    {
        File serverJarFile = new File(getEmbeddedTomcatJarPath())
        if (!serverJarFile.exists())
            makeEmbeddedTomcatJar()

        copyWindowsCoreUtilities()
        def utilsDir = getWindowsUtilDir()

        ant.tar(tarfile: getEmbeddedTarArchivePath(),
                longfile: "gnu",
                compression: "gzip") {
            tarfileset(dir: project.buildDir, prefix: archiveName) { include(name: serverJarFile.getName()) }

            tarfileset(dir: utilsDir.path, prefix: "${archiveName}/bin")

            tarfileset(dir: "${project.buildDir}/",
                    prefix: archiveName,
                    mode: 744) {
                include(name: "manual-upgrade.sh")
            }

            tarfileset(dir: project.buildDir, prefix: archiveName) {
                include(name: "README.txt")
                include(name: "VERSION")
                include(name: "nlp/**")
            }
        }
    }

    private void embeddedTomcatZipArchive()
    {
        copyWindowsCoreUtilities();
        def utilsDir = getWindowsUtilDir()

        File serverJarFile = new File(getEmbeddedTomcatJarPath())
        if (!serverJarFile.exists())
            makeEmbeddedTomcatJar()

        ant.zip(destfile: getEmbeddedZipArchivePath()) {
            zipfileset(dir: project.buildDir, prefix: archiveName) { include(name: serverJarFile.getName()) }
            zipfileset(dir: utilsDir.path, prefix: "${archiveName}/bin")
            zipfileset(dir: project.buildDir, prefix: archiveName, filemode: 744){
                include(name: "manual-upgrade.sh")
            }

            zipfileset(dir: "${project.buildDir}/",
                    prefix: "${archiveName}") {
                include(name: "README.txt")
                include(name: "VERSION")
                include(name: "nlp/**")
            }
        }
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
