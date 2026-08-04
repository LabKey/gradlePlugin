/*
 * Copyright (c) 2026 LabKey Corporation
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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.labkey.gradle.plugin.Api
import org.labkey.gradle.plugin.FileModule
import org.labkey.gradle.plugin.ServerDeploy
import org.labkey.gradle.plugin.extension.ServerDeployExtension
import org.labkey.gradle.util.BuildUtils

import javax.inject.Inject

/**
 * Removes a module's .module file and the directory it was unjarred into from the deploy directory, as well as its
 * .module file in the staging directory and its api jar files.
 */
@UntrackedTask(because="Does only file removal")
abstract class UndeployModule extends DefaultTask
{
    @Inject abstract FileSystemOperations getFs()

    @Internal
    final abstract Property<String> moduleName = project.objects.property(String).convention(project.name)

    @Internal
    final abstract DirectoryProperty deployModulesDir = project.objects.directoryProperty().fileValue(new File(ServerDeployExtension.getModulesDeployDirectory(project)))

    @Internal
    final abstract DirectoryProperty stagingModulesDir = BuildUtils.getRootBuildDirectoryProperty(project, ServerDeploy.STAGING_MODULES_DIR)

    // It may seem proper to make this deletion part of the project's clean task since the jar file is put there by the
    // build task, but since the copy is more of a deployment task than a build task and removing it will affect the
    // running server, we do it here instead
    @Internal
    final abstract ConfigurableFileCollection modulesApiJars = project.objects.fileCollection().from(Api.getModulesApiJars(project))

    @Internal
    final abstract Property<Boolean> useLocalBuild = project.objects.property(Boolean).convention(project.hasProperty("useLocalBuild") && "false" != project.property("useLocalBuild"))

    @Internal
    final abstract RegularFileProperty restartTriggerFile = project.objects.fileProperty().fileValue(BuildUtils.getRestartTriggerFile(project))

    @TaskAction
    void action()
    {
        // the files are deleted one at a time, and in this order, because the deploy directory for a module can be
        // recreated by listeners if its .module file is still present when the directory is deleted
        FileModule.getModuleFilesAndDirectories(moduleName.get(), deployModulesDir.get().asFile, stagingModulesDir.get().asFile)
                .forEach({ File file ->
                    logger.info("Deleting ${file}")
                    fs.delete({ DeleteSpec spec -> spec.delete(file) })
                })
        fs.delete({ DeleteSpec spec -> spec.delete(modulesApiJars) })
        BuildUtils.updateRestartTriggerFile(useLocalBuild.get(), restartTriggerFile.get().asFile)
    }
}