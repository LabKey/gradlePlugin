/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.labkey.gradle.plugin.ServerDeploy
import org.labkey.gradle.util.BuildUtils

import javax.inject.Inject

@DisableCachingByDefault(because="Outputs are in the staging directory")
abstract class StageModules extends DefaultTask
{
    @Inject abstract FileSystemOperations getFs()

    @OutputDirectory
    final abstract DirectoryProperty stagingModulesDir = BuildUtils.getRootBuildDirectoryProperty(project, ServerDeploy.STAGING_MODULES_DIR)

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getDownloadedModules()

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getBuiltModules()

    @TaskAction
    void action()
    {
        fs.delete({
            it.delete(stagingModulesDir.get())
        })
        // copy over the module dependencies first (things not built from source that might bring in
        // transitive dependencies)
        if (!getDownloadedModules().isEmpty())
        {
            ant.copy(
                    todir: stagingModulesDir.get(),
                    preserveLastModified: true // this is important so we don't re-explode modules that have not changed
            )
                {
                    getDownloadedModules().addToAntBuilder(ant, "fileset", FileCollection.AntType.FileSet)
                }
        }

        // Then copy over the project dependencies (things built from source) so they will replace
        // any transitive dependencies that were brought in).
        // One might like to do this overriding/overwriting using DependencySubstitution, as that is very much
        // what it is designed for, but that allows substitution of a project for an ExternalModuleDependency
        // and since a .module file is only one of the artifacts produced by our projects (e.g., :server:modules:platform:experiment)
        // and is not the default artifact, DependencySubstitution does not seem to work.
        // See BuildUtils.substituteModuleDependencies for an almost-working attempt at this.
        if (!getBuiltModules().isEmpty())
        {
            ant.copy(
                    overwrite: true, // overwrite existing files even if the destination files are newer
                    todir: stagingModulesDir.get(),
                    preserveLastModified: true // this is important so we don't re-explode modules that have not changed
            )
                {
                    getBuiltModules().addToAntBuilder(ant, "fileset", FileCollection.AntType.FileSet)
                }
        }
    }
}
