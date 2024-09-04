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

import org.apache.commons.lang3.StringUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.labkey.gradle.plugin.ApplyLicenses
import org.labkey.gradle.plugin.extension.DistributionExtension
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.StagingExtension
import org.labkey.gradle.util.BuildUtils

import java.nio.file.Files

class ModuleDistribution extends DefaultTask
{
    // TODO: Remove this unused property
    @Optional @Input
    String embeddedArchiveType = null

    @Optional @Input
    String extraFileIdentifier = null
    @Optional @Input
    String versionPrefix = null
    @Optional @Input
    final abstract Property<String> subDirName = project.objects.property(String).convention(project.name)
    @Optional @Input
    String archivePrefix = "LabKey"
    @Optional @Input
    String archiveName = null
    @Input
    boolean simpleDistribution = false // Set to true to exclude pipeline tools and remote pipeline libraries

    private File distributionDir

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
    File getDistributionDir()
    {
        if (distributionDir == null) {
            distributionDir = project.file("${distExtension.dir}/" + subDirName.get())
        }
        return distributionDir
    }

    @OutputFiles
    List<File> getDistFiles()
    {
        List<File> distFiles = new ArrayList<>()

        distFiles.add(new File(getEmbeddedTomcatJarPath()))
        distFiles.add(new File(getEmbeddedTarArchivePath()))
        distFiles.add(getDistributionFile())
        distFiles.add(getVersionFile())

        return distFiles
    }

    @TaskAction
    void doAction()
    {
        if (LabKeyExtension.isDevMode(project) && !project.hasProperty("devDistribution"))
            throw new GradleException("Distributions should never be created with deployMode=dev as dev modules are not portable. " +
                    "Use -PdevDistribution if you need to override this exception for debugging.")

        createDistributionFiles()
        gatherModules()
        embeddedTomcatTarArchive()
    }

    @OutputDirectory
    File getModulesDir()
    {
        // we use a common directory to save on disk space for TeamCity.
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

    @Input
    String getArtifactId()
    {
        return subDirName.get()
    }

    String getArchiveName()
    {
        if (archiveName == null)
        {
            var extraIdentifier = extraFileIdentifier != null ? extraFileIdentifier : "-" + project.name
            archiveName = "${archivePrefix}${BuildUtils.getDistributionVersion(project)}" + extraIdentifier
        }
        return archiveName
    }

    private String getEmbeddedTomcatJarPath()
    {
        return new File(getModulesDir(), "labkeyServer.jar").path
    }

    private String getDistributionZipPath()
    {
        return new File(getModulesDir(), "labkey/distribution.zip").path
    }

    private String getEmbeddedTarArchivePath()
    {
        return "${getDistributionDir()}/${getArchiveName()}${DistributionExtension.EMBEDDED_SUFFIX}.${DistributionExtension.TAR_ARCHIVE_EXTENSION}"
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

    private makeEmbeddedTomcatJar()
    {
        StagingExtension staging = project.getExtensions().getByType(StagingExtension.class)

        File embeddedJarFile = project.configurations.embedded.singleFile
        String modulesZipFile = getDistributionZipPath()
        File serverJarFile = new File(getEmbeddedTomcatJarPath())
        ant.zip(destFile: modulesZipFile) {
            zipfileset(dir: getModulesDir(),
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
            fileset(dir: "${getModulesDir()}", includes: "labkey/**")
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

            tarfileset(dir: "${BuildUtils.getBuildDirPath(project)}/embedded", prefix: archiveName)
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
        File serverConfigDir = project.rootProject.file("server/configs/webapps/")
        if (serverConfigDir.exists()) {
            project.copy({ CopySpec copy ->
                copy.from(serverConfigDir)
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
        // Include TeamCity buildUrl, if present.
        def buildUrl = StringUtils.trimToEmpty(System.getenv("BUILD_URL"))
        Files.write(getVersionFile().toPath(), "${project.version}\n${buildUrl}".trim().getBytes())
    }
}
