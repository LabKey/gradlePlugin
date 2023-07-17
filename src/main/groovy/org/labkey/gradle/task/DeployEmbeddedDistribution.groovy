package org.labkey.gradle.task

import org.apache.commons.io.FilenameUtils
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.extension.DistributionExtension

class DeployEmbeddedDistribution extends DeployAppBase {

    @OutputDirectory
    File deployDir = new File((String) project.serverDeploy.embeddedDir)

    @OutputDirectory
    File deployBinDir = new File((String) project.serverDeploy.embeddedDir, "bin")

    @TaskAction
    void action()
    {
        deployExecutableJar()
        deployNlpEngine(deployBinDir)
        deployPlatformBinaries(deployBinDir)
    }

    private void deployExecutableJar() {
        File distributionFile = DistributionExtension.getDistributionFile(project, true)
        boolean isTar = FilenameUtils.getExtension(distributionFile.getName()).equals("gz")
        project.copy({ CopySpec copy ->
            copy.from isTar ? project.tarTree(distributionFile).files : project.zipTree(distributionFile).files
            copy.into deployDir
            copy.include  "*.jar"
            copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        })
        project.copy({ CopySpec copy ->
            copy.from isTar ? project.tarTree(distributionFile).files : project.zipTree(distributionFile).files
            copy.into deployBinDir
            copy.include   "*.exe", "*.dll"
            copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        })
    }
}
