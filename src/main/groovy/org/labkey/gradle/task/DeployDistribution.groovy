package org.labkey.gradle.task

import org.gradle.api.file.CopySpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.ServerDeploy
import org.labkey.gradle.plugin.extension.DistributionExtension
import org.labkey.gradle.plugin.extension.ServerDeployExtension

abstract class DeployDistribution extends DeployAppBase {

    @OutputDirectory
    final abstract DirectoryProperty deployDir = project.objects.directoryProperty().convention(ServerDeployExtension.getEmbeddedDir(project))

    @OutputDirectory
    final abstract DirectoryProperty deployBinDir = project.objects.directoryProperty().convention(ServerDeployExtension.getEmbeddedBinDir(project))

    @TaskAction
    void action()
    {
        deployExecutableJar()
        deployPlatformBinaries(deployBinDir.get().asFile)
        setUpProperties()
    }

    private void deployExecutableJar() {
        File distributionFile = DistributionExtension.getDistributionFile(project)
        fs.copy({ CopySpec copy ->
            copy.from project.tarTree(distributionFile).files
            copy.into deployDir.get()
            copy.include  "*.jar"
            copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        })
        fs.copy({ CopySpec copy ->
            copy.from project.tarTree(distributionFile).files
            copy.into deployBinDir.get()
            copy.include   "*.exe", "*.dll"
            copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        })
    }
}
