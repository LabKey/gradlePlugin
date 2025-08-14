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

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.ServerDeploy
import org.labkey.gradle.plugin.extension.ServerDeployExtension
import org.labkey.gradle.util.BuildUtils

abstract class DeployApp extends DeployAppBase
{
    @InputDirectory
    final abstract DirectoryProperty stagingModulesDir = BuildUtils.getRootBuildDirectoryProperty(project, ServerDeploy.STAGING_MODULES_DIR)

    @InputDirectory
    final abstract DirectoryProperty stagingPipelineJarDir = BuildUtils.getRootBuildDirectoryProperty(project, ServerDeploy.STAGING_PIPELINE_DIR)
    
    @OutputDirectory
    final abstract DirectoryProperty deployModulesDir = BuildUtils.getRootBuildDirectoryProperty(project, ServerDeploy.DEPLOY_MODULES_DIR)

    // We declare this as an output so it will be created by this task, even though not actually populated here
    @OutputDirectory
    final abstract DirectoryProperty deployWebappDir = BuildUtils.getRootBuildDirectoryProperty(project, ServerDeploy.DEPLOY_WEBAPP_DIR)

    @OutputDirectory
    final abstract DirectoryProperty deployPipelineLibDir = BuildUtils.getRootBuildDirectoryProperty(project, ServerDeploy.DEPLOY_PIPELINE_DIR)

    @OutputDirectory
    final abstract DirectoryProperty deployBinDir = BuildUtils.getRootBuildDirectoryProperty(project, ServerDeploy.DEPLOY_BIN_DIR)

    @Input
    final abstract Property<Boolean> useLocalBuild = project.objects.property(Boolean).convention(project.hasProperty("useLocalBuild") && "false" != project.property("useLocalBuild"))

    @OutputFile
    final abstract RegularFileProperty restartTriggerFile = project.objects.fileProperty().fileValue(BuildUtils.getRestartTriggerFile(project))

    @OutputDirectory
    final abstract DirectoryProperty embeddedDir = project.objects.directoryProperty().convention(ServerDeployExtension.getEmbeddedServerDeployDirectory(project))

    @InputFiles
    abstract ConfigurableFileCollection getBootJar()

    @TaskAction
    void action()
    {
        deployModules()
        deployPipelineJars()
        deployPlatformBinaries(deployBinDir.get().asFile)
        deployEmbeddedBootJar()
        setUpProperties()
        BuildUtils.updateRestartTriggerFile(useLocalBuild.get(), restartTriggerFile.get().asFile)
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

    private void deployEmbeddedBootJar()
    {
        fs.copy {
            CopySpec copy ->
                copy.from bootJar
                copy.into embeddedDir.get()
                copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        }
    }
}
