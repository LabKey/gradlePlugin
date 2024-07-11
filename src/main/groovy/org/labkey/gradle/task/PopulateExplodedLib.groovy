package org.labkey.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.JavaModule
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.util.TaskUtils

import javax.inject.Inject

abstract class PopulateExplodedLib extends DefaultTask
{
    @Inject abstract FileSystemOperations getFs()

    @OutputDirectory
    final abstract DirectoryProperty directory = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("${LabKeyExtension.EXPLODED_MODULE_DIR_NAME}/lib").get())

    @TaskAction
    void action() {
        List<String> copyFromTasks = JavaModule.JAR_TASK_NAMES + "copyExternalLibs"
        fs.delete {
            it.delete(directory.get())
        }
        fs.copy {
            into directory.get()
            // TODO this won't work with the configuration cache. We likely need to declare
            // these tasks as inputs to this task instead.
            for (String taskName : copyFromTasks)
                TaskUtils.doIfTaskPresent(project, taskName, task -> {
                    from task
                })
        }
    }
}
