package org.labkey.gradle.task

import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskInstantiationException

class CopyAndInstallRPackage extends InstallRPackage
{
    @InputDirectory
    File packageLocation

    private String packageName

    CopyAndInstallRPackage()
    {
        if (getPackageNames() == null || getPackageNames().isEmpty())
        {
            throw new TaskInstantiationException("Task did not specify a package to install.")
        }
        packageName = getPackageNames().get(0)
    }

    @TaskAction
    void doInstall()
    {
        super.doInstall() // Install dependencies
        File rLibsUserDir = getInstallDir()
        project.copy {
            CopySpec copy ->
                copy.from packageLocation
                copy.into(rLibsUserDir)
                copy.include(packageName + "*.tar.gz")
                copy.rename(packageName + ".*.tar.gz", packageName + ".tar.gz")
        }
        installFromArchive(packageName + ".tar.gz")
    }

}
