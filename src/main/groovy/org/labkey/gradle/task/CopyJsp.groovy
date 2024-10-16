package org.labkey.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

abstract class CopyJsp extends DefaultTask
{
    public static final String WEBAPP_DIR = "jspWebappDir/webapp"

    @Inject abstract FileSystemOperations getFs()

    @InputDirectory
    final abstract DirectoryProperty srcDir = project.objects.directoryProperty().convention(project.layout.projectDirectory.dir('src'))

    @OutputDirectory
    final abstract DirectoryProperty webappDir = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir(WEBAPP_DIR).get())

    @TaskAction
    void action() {
        fs.delete {
            it.delete(webappDir.get().dir("org"))
        }
        fs.copy {
            from srcDir.get()
            into webappDir.get()
            include '**/*.jsp'
        }
    }
}
