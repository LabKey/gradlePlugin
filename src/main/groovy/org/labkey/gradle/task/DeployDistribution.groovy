package org.labkey.gradle.task

import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.extension.DistributionExtension
import org.labkey.gradle.plugin.extension.ServerDeployExtension

import javax.inject.Inject

abstract class DeployDistribution extends DeployAppBase
{
    @Inject abstract ArchiveOperations getArchiveOps()

    @OutputDirectory
    final abstract DirectoryProperty deployDir = project.objects.directoryProperty().convention(ServerDeployExtension.getEmbeddedDir(project))

    @OutputDirectory
    final abstract DirectoryProperty deployBinDir = project.objects.directoryProperty().convention(ServerDeployExtension.getEmbeddedBinDir(project))

    @InputFile
    final abstract RegularFileProperty distributionFile = project.objects.fileProperty().fileValue(DistributionExtension.getDistributionFile(project))

    @TaskAction
    void action()
    {
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
