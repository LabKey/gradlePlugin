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

import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class DeployApp extends DeployAppBase
{
    @InputDirectory
    File stagingModulesDir = new File((String) project.staging.modulesDir)

    @InputDirectory
    File stagingPipelineJarDir = new File((String) project.staging.pipelineLibDir)
    
    @OutputDirectory
    File deployModulesDir = new File((String) project.serverDeploy.modulesDir)

    @OutputDirectory
    File deployWebappDir = new File((String) project.serverDeploy.webappDir)

    @OutputDirectory
    File deployPipelineLibDir = new File((String) project.serverDeploy.pipelineLibDir)

    @OutputDirectory
    File deployBinDir = new File((String) project.serverDeploy.binDir)

    @TaskAction
    void action()
    {
        deployModules()
        deployPipelineJars()
        deployNlpEngine(deployBinDir)
        deployPlatformBinaries(deployBinDir)
    }

    private void deployModules()
    {
        ant.copy (
                todir: deployModulesDir,
                preserveLastModified: true,
        )
                {
                    fileset(dir: stagingModulesDir)
                }
    }

    private void deployPipelineJars()
    {
        project.copy( { CopySpec copy ->
            copy.from stagingPipelineJarDir
            copy.into deployPipelineLibDir
            copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        })
    }
}
