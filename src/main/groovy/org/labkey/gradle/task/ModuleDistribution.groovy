/*
 * Copyright (c) 2017-2026 LabKey Corporation
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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.labkey.gradle.plugin.ApplyLicenses
import org.labkey.gradle.plugin.extension.DistributionExtension
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames

import javax.inject.Inject

@CacheableTask
abstract class ModuleDistribution extends DefaultTask
{
    @Inject abstract FileSystemOperations getFs()

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

    @Input
    final abstract Property<Boolean> isDevMode = project.objects.property(Boolean).convention(LabKeyExtension.isDevMode(project))

    @Input
    final abstract Property<Boolean> isDevDist = project.objects.property(Boolean).convention(project.hasProperty("devDistribution"))

    @Internal
    final abstract Property<Boolean> isOpenSource = project.objects.property(Boolean).convention(BuildUtils.isOpenSource(project))

    // Used to derive the default name of the distribution when neither extraFileIdentifier nor subDirName is provided
    @Input
    final abstract Property<String> projectName = project.objects.property(String).convention(project.name)

    @Input
    final abstract Property<String> projectVersion = project.objects.property(String).convention(project.getVersion().toString())

    @Input
    final abstract Property<String> distributionVersion = project.objects.property(String).convention(BuildUtils.getDistributionVersion(project))

    // The directory the distribution subdirectories are created in, from the 'dist' extension
    @Internal
    final abstract DirectoryProperty distributionsDir = project.objects.directoryProperty().fileValue(findDistributionsDir(project))

    @Internal
    final abstract DirectoryProperty buildDir = project.objects.directoryProperty().convention(project.layout.buildDirectory)

    @OutputDirectory
    // we use a common directory to save on disk space for TeamCity.
    final abstract DirectoryProperty modulesDir = project.objects.directoryProperty().fileValue(new File("${BuildUtils.getRootBuildDirPath(project)}/distModules"))

    @OutputFile
    final abstract RegularFileProperty distributionPropertiesFile = project.objects.fileProperty().fileValue(BuildUtils.getBuildDirFile(project, DistributionExtension.DIST_PROPERTIES_FILE_NAME))

    // Files from 'server/configs/webapps' are preferred, if this directory exists
    @Internal
    final abstract DirectoryProperty serverConfigsDir = project.objects.directoryProperty().fileValue(project.rootProject.file("server/configs/webapps/"))

    // Allows distributions to include a custom README
    @Internal
    final abstract DirectoryProperty resourcesDir = project.objects.directoryProperty().fileValue(project.file("resources"))

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    final abstract ConfigurableFileCollection distributionModules = project.objects.fileCollection().from(project.configurations.distribution)

    // Not an input because it is only resolved when the embedded server jar has to be built
    @Internal
    final abstract ConfigurableFileCollection embeddedServerJar = project.objects.fileCollection().from(project.configurations.embedded)

    // The api module patched with the commercial license libraries. Empty for an open source distribution.
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    final abstract ConfigurableFileCollection patchedApiModule = project.objects.fileCollection()

    ModuleDistribution()
    {
        description = "Make a LabKey modules distribution"
        group = GroupNames.DISTRIBUTION

        Project serverProject = BuildUtils.getServerProject(project)
        this.dependsOn(serverProject.tasks.named("stageApp"))
        if (!BuildUtils.isOpenSource(project)) {
            TaskProvider patchApiTask = findLicensingProject(project).tasks.named("patchApiModule")
            this.dependsOn(patchApiTask)
            patchedApiModule.from(patchApiTask)
        }
        if (BuildUtils.embeddedProjectExists(project))
            this.dependsOn(project.project(BuildUtils.getEmbeddedProjectPath(project.gradle)).tasks.named("build"))

        project.apply plugin: 'org.labkey.build.base'
    }

    @OutputDirectory
    File getDistributionDir()
    {
        return new File(distributionsDir.get().asFile, getSubDir())
    }

    @OutputFiles
    List<File> getDistFiles()
    {
        List<File> distFiles = new ArrayList<>()

        distFiles.add(new File(getLabKeyServerJarPath()))
        distFiles.add(new File(getTarArchivePath()))

        return distFiles
    }

    @TaskAction
    void doAction()
    {
        if (isDevMode.get() && !isDevDist.get())
            throw new GradleException("Distributions should never be created with deployMode=dev as dev modules are not portable. " +
                    "Use -PdevDistribution if you need to override this exception for debugging.")

        createDistributionFiles()
        gatherModules()
        embeddedTomcatTarArchive()
    }

    private static File findDistributionsDir(Project project)
    {
        return project.file(project.extensions.getByType(DistributionExtension.class).dir)
    }

    static Project findLicensingProject(Project project)
    {
        Project licensingProject = null
        Project currProject = project
        while (licensingProject == null && currProject != null) {
            if (currProject.plugins.findPlugin(ApplyLicenses))
                licensingProject = currProject
            currProject = currProject.parent
        }

        if (!BuildUtils.isOpenSource(project) && licensingProject == null)
            throw new GradleException("Cannot build non-open source distribution. Unable to find project with the plugin org.labkey.build.applyLicenses in ${project.path} ancestors.")

        return licensingProject
    }

    private void gatherModules()
    {
        File modulesDirFile = modulesDir.get().asFile
        modulesDirFile.deleteDir()
        fs.copy {
            CopySpec copy ->
                copy.from distributionModules
                copy.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
                copy.into modulesDirFile
        }
        if (!isOpenSource.get())
        {
            fs.copy {
                CopySpec copy ->
                    copy.from(patchedApiModule.singleFile)
                    copy.rename { String fileName ->
                        fileName.replace("-extJsCommercial", "")
                    }
                    copy.into modulesDirFile
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
            archiveName = "${archivePrefix}${distributionVersion.get()}" + getFileIdentifier()
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
        int idx = projectName.get().indexOf("_dist")
        return idx == -1 ? projectName.get() : projectName.get().substring(0, idx)
    }

    private String getLabKeyServerJarPath()
    {
        return new File(modulesDir.get().asFile, "labkeyServer.jar").path
    }

    private String getDistributionZipPath()
    {
        return new File(modulesDir.get().asFile, "labkey/distribution.zip").path
    }

    private String getTarArchivePath()
    {
        return "${getDistributionDir()}/${getArchiveName()}.${DistributionExtension.TAR_ARCHIVE_EXTENSION}"
    }

    private makeEmbeddedTomcatJar()
    {
        File embeddedJarFile = embeddedServerJar.singleFile
        String modulesZipFile = getDistributionZipPath()
        File serverJarFile = new File(getLabKeyServerJarPath())
        String buildDirPath = buildDir.get().asFile.path
        ant.zip(destFile: modulesZipFile) {
            zipfileset(dir: modulesDir.get().asFile,
                    prefix: "modules") {
                include(name: "*.module")
            }
            zipfileset(dir: "${buildDirPath}/") {
                include(name: "labkeywebapp/**")
            }
            zipfileset(dir: "${buildDirPath}/",
                    prefix: "${DistributionExtension.DIST_FILE_DIR}") {
                include(name: DistributionExtension.DIST_PROPERTIES_FILE_NAME)
            }
        }

        fs.copy {
            CopySpec copy ->
                copy.from(embeddedJarFile)
                copy.into(buildDir)
                copy.rename(embeddedJarFile.getName(), serverJarFile.getName())
                copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        }

        ant.jar(
            destfile: new File(buildDir.get().asFile, serverJarFile.getName()),
            update: true,
            keepcompression: true
        ) {
            fileset(dir: "${modulesDir.get().asFile}", includes: "labkey/**")
        }
    }

    private void embeddedTomcatTarArchive()
    {
        File serverJarFile = new File(getLabKeyServerJarPath())
        if (!serverJarFile.exists())
            makeEmbeddedTomcatJar()

        String buildDirPath = buildDir.get().asFile.path
        ant.tar(tarfile: getTarArchivePath(),
                longfile: "gnu",
                compression: "gzip") {
            tarfileset(dir: buildDir.get().asFile, prefix: getArchiveName()) { include(name: serverJarFile.getName()) }

            tarfileset(dir: "${buildDirPath}/embedded", prefix: getArchiveName())
        }
    }

    private void createDistributionFiles()
    {
        writeDistributionPropertiesFile()
        // Prefer files from 'server/configs/webapps' if they exist
        File serverConfigDir = serverConfigsDir.get().asFile
        if (serverConfigDir.exists()) {
            fs.copy({ CopySpec copy ->
                copy.from(serverConfigDir)
                copy.exclude "*.xml"
                copy.into(buildDir)
                copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
            })
        }
        // Allow distributions to include custom README
        File resources = resourcesDir.get().asFile
        if (resources.isDirectory()) {
            fs.copy({ CopySpec copy ->
                copy.from(resources)
                copy.into(buildDir)
                copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
            })
            fs.copy({ CopySpec copy ->
                copy.from(resources)
                copy.into(buildDir.dir("embedded"))
                copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
            })
        }
        // This is necessary for reasons that are unclear. Without it, you get:
        // -bash: ./manual-upgrade.sh: /bin/sh^M: bad interpreter: No such file or directory
        // even though the original file has unix line endings. Dunno.
        this.ant.fixcrlf (srcdir: buildDir.get().asFile.path, includes: "manual-upgrade.sh", eol: "unix")
    }

    // Write distribution build properties and (if provided) dist.extraProperties map into distribution.properties. This
    // file is then copied into /WEB-INF/classes, making it available to the webapp.
    private void writeDistributionPropertiesFile()
    {
        // Add standard properties from the distribution build
        // Assume that fileIdentifier (usually '-' + project.name, but not guaranteed) is the canonical name
        extraProperties.put("name", StringUtils.removeStart(getFileIdentifier(), '-'))
        extraProperties.put("filename", getArchiveName() + "." + DistributionExtension.TAR_ARCHIVE_EXTENSION)
        extraProperties.put("version", projectVersion.get())

        // Include TeamCity buildUrl, if present.
        def buildUrl = StringUtils.trimToNull(System.getenv("BUILD_URL"))
        if (buildUrl != null)
            extraProperties.put("buildUrl", buildUrl)

        distributionPropertiesFile.get().asFile.withWriter { out ->
            extraProperties.each { k, v -> out.println "${k}: ${v}" }
        }
    }
}
