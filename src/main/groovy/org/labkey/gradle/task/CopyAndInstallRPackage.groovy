package org.labkey.gradle.task

import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

class CopyAndInstallRPackage extends InstallRPackage
{
    @InputDirectory
    File packageLocation

    private final String packageName

    CopyAndInstallRPackage()
    {
        if (packageNames == null || packageNames.isEmpty())
        {
            logger.error("No package name specified.")
        }
        packageName = packageNames.get(0)
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
