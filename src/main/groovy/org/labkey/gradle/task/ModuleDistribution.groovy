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
import org.gradle.api.tasks.*
import org.labkey.gradle.plugin.ApplyLicenses
import org.labkey.gradle.plugin.extension.DistributionExtension
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames

import java.nio.file.Files

class ModuleDistribution extends DefaultTask
{
    @Optional @Input
    String extraFileIdentifier = null
    @Optional @Input
    String versionPrefix = null
    @Optional @Input
    String subDirName = null
    @Optional @Input
    String archivePrefix = "LabKey"
    @Optional @Input
    String archiveName = null
    @Input
    boolean simpleDistribution = false // Set to true to exclude pipeline tools and remote pipeline libraries
    @Optional @Input
    Map extraProperties = [:]

    private File distributionDir

    private final DistributionExtension distExtension
    private Project licensingProject

    ModuleDistribution()
    {
        description = "Make a LabKey modules distribution"
        distExtension = project.extensions.findByType(DistributionExtension.class)
        group = GroupNames.DISTRIBUTION

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
            distributionDir = project.file("${distExtension.dir}/" + getSubDir())
        }
        return distributionDir
    }

    @OutputFiles
    List<File> getDistFiles()
    {
        List<File> distFiles = new ArrayList<>()

        distFiles.add(new File(getLabKeyServerJarPath()))
        distFiles.add(new File(getTarArchivePath()))
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
        return getSubDir()
    }

    String getArchiveName()
    {
        if (archiveName == null)
        {
            archiveName = "${archivePrefix}${BuildUtils.getDistributionVersion(project)}" + getFileIdentifier()
        }
        return archiveName
    }

    private String getFileIdentifier()
    {
        return extraFileIdentifier != null ? extraFileIdentifier : "-" + getDefaultName()
    }

    private String getSubDir()
    {
        return subDirName == null ? getDefaultName() : subDirName
    }

    // Standard name to use when extraFileIdentifier or subDirName property isn't provided
    private String getDefaultName()
    {
        int idx = project.name.indexOf("_dist")
        return idx == -1 ? project.name : project.name.substring(0, idx)
    }

    private String getLabKeyServerJarPath()
    {
        return new File(getModulesDir(), "labkeyServer.jar").path
    }

    private String getDistributionZipPath()
    {
        return new File(getModulesDir(), "labkey/distribution.zip").path
    }

    private String getTarArchivePath()
    {
        return "${getDistributionDir()}/${getArchiveName()}.${DistributionExtension.TAR_ARCHIVE_EXTENSION}"
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
        File embeddedJarFile = project.configurations.embedded.singleFile
        String modulesZipFile = getDistributionZipPath()
        File serverJarFile = new File(getLabKeyServerJarPath())
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
                include(name: DistributionExtension.VERSION_FILE_NAME)
            }
            zipfileset(dir: "${BuildUtils.getBuildDirPath(project)}/",
                    prefix: "${DistributionExtension.DIST_FILE_DIR}") {
                include(name: DistributionExtension.DIST_PROPERTIES_FILE_NAME)
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
        File serverJarFile = new File(getLabKeyServerJarPath())
        if (!serverJarFile.exists())
            makeEmbeddedTomcatJar()

        copyWindowsCoreUtilities()
        def utilsDir = getWindowsUtilDir()

        ant.tar(tarfile: getTarArchivePath(),
                longfile: "gnu",
                compression: "gzip") {
            tarfileset(dir: BuildUtils.getBuildDir(project), prefix: archiveName) { include(name: serverJarFile.getName()) }

            if (!simpleDistribution) {
                tarfileset(dir: utilsDir.path, prefix: "${archiveName}/bin")
            }

            tarfileset(dir: BuildUtils.getBuildDir(project), prefix: archiveName) {
                include(name: DistributionExtension.VERSION_FILE_NAME)
            }

            tarfileset(dir: "${BuildUtils.getBuildDirPath(project)}/embedded", prefix: archiveName)
        }
    }

    private void createDistributionFiles()
    {
        writeDistributionFile()
        writeVersionFile()
        writeDistributionPropertiesFile()
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
        // This is necessary for reasons that are unclear. Without it, you get:
        // -bash: ./manual-upgrade.sh: /bin/sh^M: bad interpreter: No such file or directory
        // even though the original file has unix line endings. Dunno.
        project.ant.fixcrlf (srcdir: BuildUtils.getBuildDirPath(project), includes: "manual-upgrade.sh", eol: "unix")
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

    @OutputFile
    File getDistributionPropertiesFile()
    {
        return BuildUtils.getBuildDirFile(project, DistributionExtension.DIST_PROPERTIES_FILE_NAME)
    }

    // Write distribution build properties and (if provided) dist.extraProperties map into distribution.properties. This
    // file is then copied into /WEB-INF/classes, making it available to the webapp.
    private void writeDistributionPropertiesFile()
    {
        // Add standard properties from the distribution build
        // Assume that fileIdentifier (usually '-' + project.name, but not guaranteed) is the canonical name
        extraProperties.put("name", StringUtils.removeStart(getFileIdentifier(), '-'))
        extraProperties.put("filename", getArchiveName() + "." + DistributionExtension.TAR_ARCHIVE_EXTENSION)

        getDistributionPropertiesFile().withWriter { out ->
            extraProperties.each { k, v -> out.println "${k}: ${v}" }
        }
    }
}
