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
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.*
import org.labkey.gradle.plugin.ApplyLicenses
import org.labkey.gradle.plugin.extension.DistributionExtension
import org.labkey.gradle.plugin.extension.LabKeyExtension
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
    @Input
    boolean simpleDistribution = false // Set to true to exclude pipeline tools and remote pipeline libraries

    @OutputDirectory
    File distributionDir

    private final DistributionExtension distExtension
    private Project licensingProject

    ModuleDistribution()
    {
        description = "Make a LabKey modules distribution"
        distExtension = project.extensions.findByType(DistributionExtension.class)

        Project serverProject = BuildUtils.getServerProject(project)
        this.dependsOn(serverProject.tasks.named("stageApp"))
        if (!BuildUtils.isOpenSource(project)) {
            this.dependsOn(findLicensingProject().tasks.named("patchApiModule"))
        }
        if (BuildUtils.embeddedProjectExists(project))
            this.dependsOn(project.project(BuildUtils.getEmbeddedProjectPath(project.gradle)).tasks.named("build"))

        project.apply plugin: 'org.labkey.build.base'
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
        if (LabKeyExtension.isDevMode(project) && !project.hasProperty("devDistribution"))
            throw new GradleException("Distributions should never be created with deployMode=dev as dev modules are not portable. " +
                    "Use -PdevDistribution if you need to override this exception for debugging.")

        if (makeDistribution)
            createDistributionFiles()
        gatherModules()
        packageRedistributables()

    }

    @OutputDirectory
    File getModulesDir()
    {
        // we use a common directory to save on disk space for TeamCity.
        // (This is just a conjecture about why it continues to run out of space and not be able to copy files from one place to the other).
        return new File("${BuildUtils.getRootBuildDirPath(project)}/distModules")
    }

    Project findLicensingProject()
    {
        if (licensingProject == null) {
            Project currProject = project
            while (licensingProject == null && currProject != null) {
                if (currProject.plugins.findPlugin(ApplyLicenses))
                    licensingProject = currProject
                currProject = currProject.parent
            }

            if (!BuildUtils.isOpenSource(project) && licensingProject == null)
                throw new GradleException("Cannot build non-open source distribution. Unable to find project with the plugin org.labkey.build.applyLicenses in ${project.path} ancestors.")
        }
        return licensingProject
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
        if (!BuildUtils.isOpenSource(project))
        {
            project.copy {
                CopySpec copy ->
                    copy.from(findLicensingProject().tasks.patchApiModule.outputs.files.singleFile)
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
            copyLabkeyXml()
        }
        packageArchives()
    }

    private void copyLabkeyXml()
    {
        Properties copyProps = new Properties()
        // Pre-configure labkey.xml to work with postgresql
        copyProps.put("jdbcURL", "jdbc:postgresql://localhost/labkey")
        copyProps.put("jdbcDriverClassName", "org.postgresql.Driver")

        project.copy
        { CopySpec copy ->
            copy.from(BuildUtils.getWebappConfigFile(project, "labkey.xml"))
            copy.into(project.layout.buildDirectory)
            copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
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
        return BuildUtils.getBuildDirFile(project, "labkeyServer.jar").path
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
                    copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
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

                if (!simpleDistribution) {
                    tarfileset(dir: utilsDir.path, prefix: "${archiveName}/bin")

                    tarfileset(dir: staging.pipelineLibDir, prefix: "${archiveName}/pipeline-lib")
                }

                tarfileset(dir: "${BuildUtils.getBuildDirPath(project)}/",
                        prefix: archiveName,
                        mode: 744) {
                    include(name: "manual-upgrade.sh")
                }

                tarfileset(dir: BuildUtils.getBuildDir(project),
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
            copyWindowsCoreUtilities()
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

                if (!simpleDistribution) {
                    zipfileset(dir: utilsDir.path, prefix: "${archiveName}/bin")

                    zipfileset(dir: staging.pipelineLibDir, prefix: "${archiveName}/pipeline-lib")
                }

                zipfileset(dir: "${BuildUtils.getBuildDirPath(project)}/",
                        prefix: "${archiveName}",
                        filemode: 744){
                    include(name: "manual-upgrade.sh")
                }

                zipfileset(dir: "${BuildUtils.getBuildDirPath(project)}/",
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

        File embeddedJarFile = project.configurations.embedded.singleFile
        File modulesZipFile = BuildUtils.getBuildDirFile(project,"labkey/distribution.zip")
        File serverJarFile = new File(getEmbeddedTomcatJarPath())
        ant.zip(destFile: modulesZipFile.getAbsolutePath()) {
            zipfileset(dir: staging.webappDir,
                    prefix: "labkeywebapp") {
                exclude(name: "WEB-INF/classes/distribution")
            }
            zipfileset(dir: BuildUtils.getRootBuildDirFile(project, "distModules"),
                    prefix: "modules") {
                include(name: "*.module")
            }
            zipfileset(dir: "${BuildUtils.getBuildDirPath(project)}/") {
                include(name: "labkeywebapp/**")
            }
            zipfileset(dir: "${BuildUtils.getBuildDirPath(project)}/",
                    prefix: "${DistributionExtension.DIST_FILE_DIR}") {
                include(name: "VERSION")
            }
        }

        project.copy {
            CopySpec copy ->
                copy.from(embeddedJarFile)
                copy.into(project.layout.buildDirectory)
                copy.rename(embeddedJarFile.getName(), serverJarFile.getName())
                copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        }

        ant.jar(
            destfile: BuildUtils.getBuildDirFile(project, serverJarFile.getName()),
            update: true,
            keepcompression: true
        ) {
            fileset(dir: "${BuildUtils.getBuildDirPath(project)}", includes: "labkey/**")
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
            tarfileset(dir: BuildUtils.getBuildDir(project), prefix: archiveName) { include(name: serverJarFile.getName()) }

            if (!simpleDistribution) {
                tarfileset(dir: utilsDir.path, prefix: "${archiveName}/bin")
            }

            tarfileset(dir: BuildUtils.getBuildDir(project), prefix: archiveName) {
                include(name: "VERSION")
            }

            tarfileset(dir: "${BuildUtils.getBuildDirPath(project)}/embedded", prefix: archiveName) {
                exclude(name: "manual-upgrade.sh")
            }
        }
    }

    private void embeddedTomcatZipArchive()
    {
        copyWindowsCoreUtilities()
        def utilsDir = getWindowsUtilDir()

        File serverJarFile = new File(getEmbeddedTomcatJarPath())
        if (!serverJarFile.exists())
            makeEmbeddedTomcatJar()

        ant.zip(destfile: getEmbeddedZipArchivePath()) {
            zipfileset(dir: BuildUtils.getBuildDir(project), prefix: archiveName) { include(name: serverJarFile.getName()) }

            if (!simpleDistribution) {
                zipfileset(dir: utilsDir.path, prefix: "${archiveName}/bin")
            }

            zipfileset(dir: "${BuildUtils.getBuildDirPath(project)}/",
                    prefix: "${archiveName}") {
                include(name: "VERSION")
            }

            zipfileset(dir: "${BuildUtils.getBuildDirPath(project)}/embedded/", prefix: "${archiveName}") {
                exclude(name: "manual-upgrade.sh")
            }
        }
    }

    private void createDistributionFiles()
    {
        writeDistributionFile()
        writeVersionFile()
        FileTree zipFile = getDistributionResources(project)
        project.copy({ CopySpec copy ->
            copy.from(zipFile)
            copy.exclude "*.xml"
            copy.into(project.layout.buildDirectory)
            copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        })
        // Prefer files from 'server/configs/webapps' if they exist
        if (BuildUtils.getWebappConfigDir(project) != null) {
            project.copy({ CopySpec copy ->
                copy.from(BuildUtils.getWebappConfigDir(project))
                copy.exclude "*.xml"
                copy.into(project.layout.buildDirectory)
                copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
            })
        }
        // Allow distributions to include custom README
        File resources = project.file("resources")
        if (resources.isDirectory()) {
            project.copy({ CopySpec copy ->
                copy.from(resources)
                copy.into(project.layout.buildDirectory)
                copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
            })
            project.copy({ CopySpec copy ->
                copy.from(resources)
                copy.into(project.layout.buildDirectory.file("embedded"))
                copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
            })
        }
        // This is necessary for reasons that are unclear.  Without it, you get:
        // -bash: ./manual-upgrade.sh: /bin/sh^M: bad interpreter: No such file or directory
        // even though the original file has unix line endings. Dunno.
        project.ant.fixcrlf (srcdir: BuildUtils.getBuildDirPath(project), includes: "manual-upgrade.sh", eol: "unix")
    }

    static FileTree getDistributionResources(Project project) {
        // This seems a very convoluted way to get to the zip file in the jar file.  Using the classLoader did not
        // work as expected, however.  Following the example from here:
        // https://discuss.gradle.org/t/gradle-plugin-copy-directory-tree-with-files-from-resources/12767/7
        FileTree jarTree = project.zipTree(ModuleDistribution.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm())

        def tree = project.zipTree(
                jarTree.matching({
                    include "distributionResources.zip"
                }).singleFile)
        return tree
    }

    private File getDistributionFile()
    {
        File distExtraDir = BuildUtils.getBuildDirFile(project, DistributionExtension.DIST_FILE_DIR)
        return new File(distExtraDir,  DistributionExtension.DIST_FILE_NAME)
    }

    private void writeDistributionFile()
    {
        Files.write(getDistributionFile().toPath(), project.name.getBytes())
    }

    @OutputFile
    File getVersionFile()
    {
        return BuildUtils.getBuildDirFile(project, DistributionExtension.VERSION_FILE_NAME)
    }

    private void writeVersionFile()
    {
        Files.write(getVersionFile().toPath(), ((String) project.version).getBytes())
    }
}
