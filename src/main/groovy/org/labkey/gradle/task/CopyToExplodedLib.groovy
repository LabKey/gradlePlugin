package org.labkey.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.JavaModule
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.TaskUtils

import javax.inject.Inject

abstract class CopyToExplodedLib extends DefaultTask
{
    @Inject abstract FileSystemOperations getFs()

    @OutputDirectory
    final abstract DirectoryProperty explodedModuleLibDir = project.objects.directoryProperty()
            .convention(project.layout.projectDirectory.dir("${BuildUtils.getRootBuildDirPath(project)}/$LabKeyExtension.EXPLODED_MODULE_DIR_NAME/lib"))

    @TaskAction
    void action()
    {
        File libDir = explodedModuleLibDir.getAsFile().get()
        if (libDir.exists())
            libDir.delete()
        List<String> copyFromTasks = JavaModule.JAR_TASK_NAMES + "copyExternalLibs"
        fs.copy {
            into explodedModuleLibDir.get()
            for (String taskName : copyFromTasks)
                TaskUtils.doIfTaskPresent(project, taskName, inputTask -> {
                    from inputTask
                })
        }
    }
}
