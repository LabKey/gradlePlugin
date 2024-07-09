package org.labkey.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction


import javax.inject.Inject

abstract class CopyJspResources extends DefaultTask
{

    @Input
    String directoryName

    @Inject abstract FileSystemOperations getFs()

    @OutputDirectory
    final abstract DirectoryProperty webappDir = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir(CopyJsp.WEBAPP_DIR).get())

    @TaskAction
    void action() {
        fs.copy {
            from 'resources'
            into webappDir.get().dir("org/labkey/${directoryName}")
            include '**/*.jsp'
            setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        }
    }

}
