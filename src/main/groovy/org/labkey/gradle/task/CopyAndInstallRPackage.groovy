package org.labkey.gradle.task

import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class CopyAndInstallRPackage extends InstallRPackage
{
    @Input
    String packageName
    @Input
    File sourceDir

    @TaskAction
    void doInstall()
    {
        super.doInstall() // Install dependencies
        File rLibsUserDir = getInstallDir()
        project.copy {
            CopySpec copy ->
                copy.from sourceDir
                copy.into(rLibsUserDir)
                copy.include(packageName + "*.tar.gz")
                copy.rename(packageName + ".*.tar.gz", packageName + ".tar.gz")
        }
        installFromArchive(packageName + ".tar.gz")
    }

}
