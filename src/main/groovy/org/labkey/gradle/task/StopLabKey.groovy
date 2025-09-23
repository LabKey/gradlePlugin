/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.extension.ServerDeployExtension

import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Task for stopping a running LabKey instance
 */
class StopLabKey extends DefaultTask
{
    @InputFile
    final abstract RegularFileProperty propertiesFile = project.objects.fileProperty().fileValue(
            BuildUtils.getApplicationPropertiesFile(project)
    )

    @Input
    final abstract Property<Boolean> useSsl = project.objects.property(Boolean).convention(project.hasProperty("useSsl"))

    @TaskAction
    void action()
    {
        def pidFile = new File(ServerDeployExtension.getLabKeyPidFile(project), "labkey.pid")

        if (pidFile.exists())
        {
            String pidStr = new String(Files.readAllBytes(pidFile.toPath()), StandardCharsets.UTF_8).trim()
            Integer pid = Integer.parseInt(pidStr)
            stopLabKeyByPid(pid)
        }

    }

    private static void stopLabKeyByPid(long pid)
    {
        ProcessHandle.of(pid).ifPresent {processHandle ->
            processHandle.onExit().thenRun {
                this.logger.quiet("LabKey gracefully terminated. pid: " + pid)
            }

            boolean terminated = processHandle.destroy()
            if (terminated) {
                this.logger.info("LabKey shutdown triggered.")
                try {
                    processHandle.onExit().wait(10_000)
                }
                catch (InterruptedException ie) {
                    this.logger.error("Failed to shutdown LabKey", ie)
                }
            }
            else {
                this.logger.error("Unable to shutdown LabKey")
            }
        }
    }

}
