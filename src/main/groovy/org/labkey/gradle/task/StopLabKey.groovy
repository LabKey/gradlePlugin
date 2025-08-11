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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.util.PropertiesUtils
import org.labkey.gradle.util.BuildUtils

/**
 * Task for stopping a running LabKey instance
 */
class StopLabKey extends DefaultTask
{
    @InputFile
    File propertiesFile = BuildUtils.getApplicationPropertiesFile(project)

    @Input
    final abstract Property<Boolean> useSsl = project.objects.property(Boolean).convention(project.hasProperty("useSsl"))

    @TaskAction
    void action()
    {
        def applicationProperties = PropertiesUtils.getApplicationProperties(propertiesFile)
        def port = applicationProperties.getProperty("management.server.port", applicationProperties.getProperty("server.port"))
        def endpoint =  "${useSsl.get() ? "https" : "http"}://localhost:$port/actuator/shutdown"
        def command = "curl -X POST $endpoint"
        this.logger.info("Sending command to $endpoint")
        def proc = command.execute()
        proc.waitFor()
        if (proc.exitValue() != 0)
            this.logger.warn("Shutdown command exited with non-zero status ${proc.exitValue()}.")
        else
            this.logger.quiet("Shutdown successful")
    }
}
