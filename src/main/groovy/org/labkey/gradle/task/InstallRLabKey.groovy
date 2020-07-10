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

import org.apache.commons.lang3.SystemUtils
import org.gradle.api.file.CopySpec
/**
 * Created by susanh on 3/16/17.
 * TODO: Remove when 20.7 is no longer supported
 */
class InstallRLabKey extends InstallRPackage
{
    @Override
    void doInstall()
    {
        File rLibsUserDir= getInstallDir()
        File remoteApiDir = project.file('../../remoteapi/r')

        project.copy {
            CopySpec copy ->
                copy.from(new File(remoteApiDir, 'test'))
                copy.into(rLibsUserDir)
                copy.include("listArchive.zip")
                copy.include("vignette.R")
                copy.include("instwin.r")
        }
        installRPackage("install-rlabkey-dependencies.R")
        if (SystemUtils.IS_OS_WINDOWS)
        {
            project.copy {
                CopySpec copy ->
                    copy.from new File(remoteApiDir,'latest')
                    copy.into rLibsUserDir
                    copy.include("Rlabkey*.zip")
            }
            project.ant.exec(dir: rLibsUserDir,  executable: getRTermPath(), input: "${rLibsUserDir}/instwin.r", failifexecutionfails: "false")
                    {
                        arg(line: "--vanilla --quiet")
                    }
        }
        else
        {
            project.copy {
                CopySpec copy ->
                    copy.from new File(remoteApiDir, 'latest')
                    copy.into(rLibsUserDir)
                    copy.include("Rlabkey*.tar.gz")
                    copy.rename("Rlabkey.*.tar.gz", "Rlabkey.tar.gz")
            }
            installFromArchive("Rlabkey.tar.gz")
        }
    }
}
