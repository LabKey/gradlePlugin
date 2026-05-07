package org.labkey.gradle.task

import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.labkey.gradle.plugin.extension.DistributionExtension
import org.labkey.gradle.plugin.extension.ServerDeployExtension

import javax.inject.Inject

@DisableCachingByDefault(because="Outputs are in the deploy directory")
abstract class DeployDistribution extends DeployAppBase
{
    @Inject abstract ArchiveOperations getArchiveOps()

    @Input
    final abstract Property<String> distDir = project.objects.property(String).convention(project.hasProperty("distDir") ? (String) project.property("distDir") : "dist")

    @OutputDirectory
    final abstract DirectoryProperty deployDir = project.objects.directoryProperty().convention(ServerDeployExtension.getEmbeddedDir(project))

    @OutputDirectory
    final abstract DirectoryProperty deployBinDir = project.objects.directoryProperty().convention(ServerDeployExtension.getEmbeddedBinDir(project))

    @InputFile @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    final abstract RegularFileProperty distributionFile = project.objects.fileProperty().fileValue(DistributionExtension.getDistributionFile(project, distDir.get()))

    @TaskAction
    void action()
    {
        if (!distributionFile.isPresent())
            throw new GradleException("Distribution file not found in directory '${distDir.get()}'. Be sure the directory exists and contains exactly one file with extension ${DistributionExtension.TAR_ARCHIVE_EXTENSION}.")

        deployExecutableJar()
        deployPlatformBinaries(deployBinDir.get().asFile)
        setDatabaseProperties()
        setUpProperties()
    }

    private void deployExecutableJar() {
        fs.copy({ CopySpec copy ->
            copy.from archiveOps.tarTree(distributionFile.get().asFile).files
            copy.into deployDir.get()
            copy.include  "*.jar"
            copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        })
        fs.copy({ CopySpec copy ->
            copy.from archiveOps.tarTree(distributionFile.get().asFile).files
            copy.into deployBinDir.get()
            copy.include   "*.exe", "*.dll"
            copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        })
    }
}
