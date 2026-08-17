/*
 * Copyright (c) 2016-2026 LabKey Corporation
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
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.labkey.gradle.plugin.extension.ServerDeployExtension

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Task for stopping a running LabKey instance
 */
@UntrackedTask(because="Output is a stopped process")
abstract class StopLabKey extends DefaultTask
{
    // file comes and goes as the server is started and stopped, so its existence must be checked when the task executes
    // rather than when the property value is captured for the configuration cache
    @Internal
    final abstract RegularFileProperty pidFile = project.objects.fileProperty().convention(ServerDeployExtension.getEmbeddedDir(project).file("labkey.pid"))

    @TaskAction
    void action()
    {
        File file = pidFile.get().asFile
        if (file.exists()) {
            String pidStr = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim()
            Integer pid = Integer.parseInt(pidStr)
            stopLabKeyByPid(pid)
        } else {
            logger.info("LabKey doesn't appear to be running in this enlistment. PID file ${file} not found")
        }
    }

    private void stopLabKeyByPid(long pid)
    {
        ProcessHandle.of(pid).ifPresentOrElse({ processHandle ->
            if (processHandle.destroy()) {
                // Wait up to 30 seconds for the process to terminate
                boolean isTerminated = processHandle.onExit().orTimeout(30, TimeUnit.SECONDS)
                        .thenApply({ handle -> true })
                        .exceptionally({ throwable -> false })
                        .get()

                if (isTerminated) {
                    logger.info("Successfully terminated LabKey process with PID: {}", pid)
                } else {
                    logger.warn("Process with PID {} did not terminate within timeout period", pid)
                }
            } else {
                logger.warn("Failed to initiate termination of LabKey process with PID: {}", pid)
            }
        }, { () -> logger.warn("No process found with PID {}", pid)} )
    }
}
