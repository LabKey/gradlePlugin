/*
 * Copyright (c) 2016-2018 LabKey Corporation
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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.UntrackedTask
import org.labkey.gradle.util.BuildUtils

import javax.inject.Inject

@UntrackedTask(because="Does only file copying and should always be run")
abstract class PickDb extends DoThenSetup
{
    @Inject abstract FileSystemOperations getFs()

    @Input
    String dbType

    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE)
    File configsDir = new File(BuildUtils.getConfigsProject(project).projectDir, "configs")

    @Override
    protected void doDatabaseTask()
    {
        //copies the correct config file.
        fs.copy({ CopySpec copy ->
            copy.from configsDir
            copy.into configsDir.parent
            copy.include "${dbType}.properties"
            copy.rename { String fileName ->
                fileName.replace(dbType, "config")
            }
            copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        })
        super.doDatabaseTask()
    }
}
