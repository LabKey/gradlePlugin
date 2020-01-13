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
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.FileModule
import org.labkey.gradle.plugin.JavaModule
import org.labkey.gradle.plugin.Module

/**
 * Removes modules from the deploy and staging directories.  If a value for dbType is provided,
 * it removes those not supporting the given dbType.  If dbType is null, removes all modules from
 * the current set of projects.
 */
class UndeployModules extends DefaultTask
{
    @Input @Optional
    String dbType = null

    @TaskAction
    void action()
    {
        project.rootProject.allprojects.each { Project p ->
            if (isLabKeyModule(p) &&
                    (dbType == null || !FileModule.shouldDoBuild(p) || !JavaModule.isDatabaseSupported(p, dbType)))
            {
                project.logger.info("Undeploying module ${p.path} for dbType ${dbType}")
                FileModule.undeployModule(p)
            }
            else
            {
                project.logger.info("Module ${p.path} left in deployment for dbType ${dbType}")
            }
        }
    }

    static boolean isLabKeyModule(Project p)
    {
        return p.plugins.findPlugin(JavaModule.class) != null ||
                p.plugins.findPlugin(Module.class) != null ||
                p.plugins.findPlugin(FileModule.class) != null
    }
}
