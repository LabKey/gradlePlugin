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
package org.labkey.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.task.GzipAction
import org.labkey.gradle.util.BuildUtils

/**
 * Declares a webapp sourceSet to capture the resource files that are copied into the webapp directory.
 * When in production mode, these static resource files are gzipped as well.
 */
class Webapp implements Plugin<Project>
{
    private static final String DIR_NAME = "webapp"

    static boolean isApplicable(Project project)
    {
        return project.file(DIR_NAME).exists()
    }

    @Override
    void apply(Project project)
    {
        project.apply plugin: 'java-base'
        addSourceSet(project)
    }

    private void addSourceSet(Project project)
    {
        project.sourceSets
                {
                    webapp {
                        resources {
                            srcDirs = [DIR_NAME]
                            // The spring configuration files are copied by the SpringConfig plugin
                            exclude "WEB-INF/${project.name}/**"
                            // when in dev mode, the webapp files will be picked up from their original locations
                            if (LabKeyExtension.isDevMode(project) && BuildUtils.shouldBuildFromSource(project))
                            {
                                include 'share/**'
                                include 'WEB-INF/**'
                            }
                            else
                            {
                                // We should only redistribute the ExtJS resource files, not the full dev kit
                                exclude "${project.labkey.ext3Dir}/src/**"
                                exclude "${project.labkey.ext4Dir}/builds/**"
                                exclude "${project.labkey.ext4Dir}/cmd/**"
                                exclude "${project.labkey.ext4Dir}/locale/**"
                                exclude "${project.labkey.ext4Dir}/src/**"
                                exclude "d3/examples/**"
                                exclude "d3/test/**"
                            }
                        }
                        output.resourcesDir = project.labkey.explodedModuleWebDir
                    }
                }
        if (!LabKeyExtension.isDevMode(project))
            project.tasks.named('processWebappResources').configure {doLast(new GzipAction())}
        project.tasks.named('processResources').configure {dependsOn('processWebappResources')}
    }
}
