/*
 * Copyright (c) 2026 LabKey Corporation
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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

/**
 * Verifies that the license files in a patched module archive match the license files in the
 * commercial-license archives the module was patched with.
 */
@DisableCachingByDefault(because="Verification task that produces no output")
abstract class VerifyLicensePatch extends DefaultTask
{
    @Inject abstract ArchiveOperations getArchiveOps()

    /** The commercial-license archives, each of which is expected to contain a single license file */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    abstract ConfigurableFileCollection getCommercialArchives()

    /** The module archive that has been patched with the commercial-license libraries */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    abstract RegularFileProperty getPatchedArchive()

    @TaskAction
    void action()
    {
        File patchedFile = patchedArchive.get().asFile
        commercialArchives.files.forEach({ File archive ->
            File commercialLicense = archiveOps.zipTree(archive).matching {
                it.include '*/license.txt'
            }.singleFile
            File patchedLicense = archiveOps.zipTree(patchedFile).matching {
                it.include 'web/' + commercialLicense.parentFile.name + '/license.txt'
            }.singleFile
            if (commercialLicense.length() != patchedLicense.length()) {
                throw new GradleException("License files didn't match for " + commercialLicense.parentFile.name)
            }
        })
    }
}
