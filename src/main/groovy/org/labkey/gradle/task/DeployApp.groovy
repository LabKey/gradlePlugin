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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.ServerDeploy

abstract class DeployApp extends DeployAppBase
{
    @InputDirectory
    final abstract DirectoryProperty stagingModulesDir = project.objects.directoryProperty().convention(project.rootProject.layout.buildDirectory.dir(ServerDeploy.STAGING_MODULES_DIR))

    @InputDirectory
    final abstract DirectoryProperty stagingPipelineJarDir = project.objects.directoryProperty().convention(project.rootProject.layout.buildDirectory.dir(ServerDeploy.STAGING_PIPELINE_DIR))
    
    @OutputDirectory
    final abstract DirectoryProperty deployModulesDir = project.objects.directoryProperty().convention(project.rootProject.layout.buildDirectory.dir(ServerDeploy.MODULES_DIR))

    // We declare this as an output so it will be created by this task, even though not actually populated here
    @OutputDirectory
    final abstract DirectoryProperty deployWebappDir = project.objects.directoryProperty().convention(project.rootProject.layout.buildDirectory.dir(ServerDeploy.WEBAPP_DIR))

    @OutputDirectory
    final abstract DirectoryProperty deployPipelineLibDir = project.objects.directoryProperty().convention(project.rootProject.layout.buildDirectory.dir(ServerDeploy.PIPELINE_DIR))

    @OutputDirectory
    final abstract DirectoryProperty deployBinDir = project.objects.directoryProperty().convention(project.rootProject.layout.buildDirectory.dir(ServerDeploy.BIN_DIR))

    @TaskAction
    void action()
    {
        deployModules()
        deployPipelineJars()
        deployPlatformBinaries(deployBinDir.get().asFile)
        updateRestartTriggerFile()
    }

    private void deployModules()
    {
        ant.copy (
                todir: deployModulesDir.get().asFile,
                preserveLastModified: true,
        )
                {
                    fileset(dir: stagingModulesDir.get().asFile)
                }
    }

    private void deployPipelineJars()
    {
        fs.copy( { CopySpec copy ->
            copy.from stagingPipelineJarDir.get().asFile
            copy.into deployPipelineLibDir.get().asFile
            copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        })
    }
}
