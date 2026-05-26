/*
 * Copyright (c) 2020-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.gradle.task

import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

@DisableCachingByDefault(because="Does only file copying")
abstract class CopyAndInstallRPackage extends InstallRPackage
{
    @Inject abstract FileSystemOperations getFs()

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
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
