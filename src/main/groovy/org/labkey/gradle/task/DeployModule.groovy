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
import org.gradle.api.file.CopySpec
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.labkey.gradle.plugin.ServerDeploy
import org.labkey.gradle.plugin.extension.ServerDeployExtension
import org.labkey.gradle.util.BuildUtils

import javax.inject.Inject

/**
 * Copies a module's .module file, and the .module files it depends on, into the staging and deploy directories.
 */
@DisableCachingByDefault(because="Outputs are in the staging and deploy directories")
abstract class DeployModule extends DefaultTask
{
    @Inject abstract FileSystemOperations getFs()

    /** The .module file for this project, together with the .module files of the modules it depends on */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    abstract ConfigurableFileCollection getModuleFiles()

    /** The name of this project's .module file, used to identify this task's output file in the deploy directory */
    @Internal
    final abstract Property<String> moduleFileName = project.objects.property(String)

    @Internal
    final abstract DirectoryProperty stagingModulesDir = BuildUtils.getRootBuildDirectoryProperty(project, ServerDeploy.STAGING_MODULES_DIR)

    @Internal
    final abstract DirectoryProperty deployModulesDir = project.objects.directoryProperty().fileValue(new File(ServerDeployExtension.getModulesDeployDirectory(project)))

    @OutputFile
    final abstract RegularFileProperty deployedModuleFile = project.objects.fileProperty().value(deployModulesDir.file(moduleFileName))

    @Input
    final abstract Property<Boolean> useLocalBuild = project.objects.property(Boolean).convention(project.hasProperty("useLocalBuild") && "false" != project.property("useLocalBuild"))

    // Not declared as an output because it is shared with the other tasks that trigger a server restart
    @Internal
    final abstract RegularFileProperty restartTriggerFile = project.objects.fileProperty().fileValue(BuildUtils.getRestartTriggerFile(project))

    @TaskAction
    void action()
    {
        copyModuleFiles(stagingModulesDir.get())
        copyModuleFiles(deployModulesDir.get())
        BuildUtils.updateRestartTriggerFile(useLocalBuild.get(), restartTriggerFile.get().asFile)
    }

    private void copyModuleFiles(Directory destination)
    {
        fs.copy({ CopySpec copy ->
            copy.from moduleFiles
            copy.into destination
            copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        })
    }
}