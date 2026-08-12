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
import org.gradle.api.Project
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.labkey.gradle.plugin.FileModule
import org.labkey.gradle.plugin.JavaModule
import org.labkey.gradle.plugin.Module
import org.labkey.gradle.plugin.ServerDeploy
import org.labkey.gradle.plugin.extension.ModuleExtension
import org.labkey.gradle.plugin.extension.ServerDeployExtension
import org.labkey.gradle.util.BuildUtils

import javax.inject.Inject

/**
 * Removes modules from the deploy and staging directories.  If a value for dbType is provided,
 * it removes those not supporting the given dbType.  If dbType is null, removes all modules from
 * the current set of projects.
 */
@UntrackedTask(because="Does only file removal")
abstract class UndeployModules extends DefaultTask
{
    @Inject abstract FileSystemOperations getFs()

    @Input @Optional
    String dbType = null

    // The project tree is walked when this task is created because the projects are not available when it executes
    private final List<ModuleInfo> moduleInfos = findModuleInfos(project)

    @TaskAction
    void action()
    {
        moduleInfos.forEach({ ModuleInfo module ->
            if (dbType == null || !module.shouldDoBuild || !module.supportsDatabase(dbType))
            {
                this.logger.info("Undeploying module ${module.path} for dbType ${dbType}")
                FileModule.getModuleFilesAndDirectories(module.name, module.deployDir, module.stagingDir)
                        .forEach({ File file -> fs.delete({ DeleteSpec spec -> spec.delete(file) }) })
            }
            else
            {
                this.logger.info("Module ${module.path} left in deployment for dbType ${dbType}")
            }
        })
    }

    private static List<ModuleInfo> findModuleInfos(Project project)
    {
        List<ModuleInfo> moduleInfos = new ArrayList<>()
        project.rootProject.allprojects.each { Project p ->
            if (isLabKeyModule(p))
                moduleInfos.add(new ModuleInfo(p))
        }
        return moduleInfos
    }

    static boolean isLabKeyModule(Project p)
    {
        return p.plugins.findPlugin(JavaModule.class) != null ||
                p.plugins.findPlugin(Module.class) != null ||
                p.plugins.findPlugin(FileModule.class) != null
    }

    /**
     * The properties of a single module that are needed to undeploy it, captured when this task is created so no
     * project is referenced while the task executes.
     */
    static class ModuleInfo implements Serializable
    {
        final String path
        final String name
        final File deployDir
        final File stagingDir
        final boolean shouldDoBuild
        final String supportedDatabases

        ModuleInfo(Project project)
        {
            path = project.path
            name = project.name
            deployDir = new File(ServerDeployExtension.getModulesDeployDirectory(project))
            stagingDir = BuildUtils.getRootBuildDirFile(project, ServerDeploy.STAGING_MODULES_DIR)
            // the message for a module that is not to be built is already logged when its plugin is applied
            shouldDoBuild = FileModule.shouldDoBuild(project, false)
            ModuleExtension extension = shouldDoBuild ? project.extensions.findByType(ModuleExtension.class) : null
            supportedDatabases = extension == null ? null : extension.getPropertyValue("SupportedDatabases")
        }

        boolean supportsDatabase(String database)
        {
            return supportedDatabases == null || supportedDatabases.contains(database)
        }
    }
}
