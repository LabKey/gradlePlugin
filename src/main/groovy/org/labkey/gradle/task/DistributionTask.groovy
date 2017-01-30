package org.labkey.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputDirectory

abstract class DistributionTask extends DefaultTask
{
    @OutputDirectory
    File dir

    @OutputDirectory
    File installerBuildDir

    DistributionTask()
    {
        dir = project.rootProject.file("dist")

        installerBuildDir = new File("${project.rootDir}/build/installer/${project.name}")
        project.mkdir(project.buildDir)
    }

}