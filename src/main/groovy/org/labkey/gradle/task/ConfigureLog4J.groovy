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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.util.BuildUtils

/**
 * Used to copy over the log4j.xml template file and replace the consoleAppender value
 * as appropriate for the current deployMode.
 */
class ConfigureLog4J extends DefaultTask
{
    @Input
    String fileName

    private File stagingDir = new File((String) project.staging.webappClassesDir)
    private File deployDir = new File("${project.serverDeploy.webappDir}/WEB-INF/classes")

    @InputFile
    File getLog4jXml()
    {
        return BuildUtils.getWebappConfigFile(project, fileName)
    }

    @OutputFile
    File getStagingFile()
    {
        return new File(stagingDir, fileName)
    }

    @OutputFile
    File getDeployFile()
    {
        return new File(deployDir, fileName)
    }

    @TaskAction
    void copyFile()
    {
        String consoleAppender = "" // this is the setting for production mode
        if (LabKeyExtension.isDevMode(project))
        {
            consoleAppender = '<appender-ref ref="CONSOLE"/>'
        }
        ant.copy(
                todir: stagingDir,
                overwrite: true,
        )
        {
            fileset(file: getLog4jXml())
            filterset(beginToken: "@@", endToken: "@@")
                    {
                        filter (token: "consoleAppender",
                                value: consoleAppender
                        )
                    }
        }
        ant.copy(
                todir: deployDir,
                overwrite: true,
        )
        {
            fileset(file: getStagingFile())
        }
    }
}
