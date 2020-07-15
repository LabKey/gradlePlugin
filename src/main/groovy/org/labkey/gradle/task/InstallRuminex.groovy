/*
 * Copyright (c) 2017 LabKey Corporation
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
import org.gradle.api.tasks.TaskAction
/**
 * Created by susanh on 3/16/17.
 * TODO: Remove when 20.7 is no longer supported
 */
class InstallRuminex extends InstallRPackage
{
    @TaskAction
    void doInstall()
    {
        File rLibsUserDir= getInstallDir()
        installRPackage("install-ruminex-dependencies.R")
        project.copy {
            CopySpec copy ->
                copy.from project.projectDir
                copy.into(rLibsUserDir)
                copy.include("Ruminex*.tar.gz")
                copy.rename("Ruminex.*.tar.gz", "Ruminex.tar.gz")
        }
        installFromArchive("Ruminex.tar.gz")
    }

}
