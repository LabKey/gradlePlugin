package org.labkey.gradle.task

import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskExecutionException

import javax.inject.Inject

abstract class CopyAndInstallRPackage extends InstallRPackage
{
    @Inject abstract FileSystemOperations getFs()

    @InputDirectory
    File packageLocation

    @TaskAction
    void doInstall()
    {
        if (getPackageNames() == null || getPackageNames().isEmpty())
        {
            throw new TaskExecutionException(this, new RuntimeException("Task did not specify a package to install."))
        }
        String packageName = getPackageNames().get(0)

        super.doInstall() // Install dependencies
        File rLibsUserDir = getInstallDir()
        fs.copy {
            CopySpec copy ->
                copy.from packageLocation
                copy.into(rLibsUserDir)
                copy.include(packageName + "*.tar.gz")
                copy.rename(packageName + ".*.tar.gz", packageName + ".tar.gz")
                copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        }
        installFromArchive(packageName + ".tar.gz")
    }

}
