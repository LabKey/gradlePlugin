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
package org.labkey.gradle.plugin

import org.gradle.api.Project
import org.labkey.gradle.util.BuildUtils

/**
 * This class is used for building a LabKey module (one that typically resides in the  server/modules
 * directory).  It defines tasks for building the jar files (<module>_api.jar, <module>_jsp.jar, <module>.jar, <module>_schemas.jar)
 * as well as tasks for copying resources to the module's build directory.  This differs from java module
 * in that it allows for a separate api jar and xml schemas classes that the compileJava tasks depend on.
 */
class Module extends JavaModule
{
    @Override
    void apply(Project project)
    {
        super.apply(project)

        if (!AntBuild.isApplicable(project) && _shouldDoBuild(project, false))
        {
            addDependencies(project)
        }
    }

    private void addDependencies(Project project)
    {
        project.dependencies
                {
                    BuildUtils.addTomcatBuildDependencies(project, 'implementation')

                    BuildUtils.addLabKeyDependency(project: project, config: "implementation", depProjectPath: BuildUtils.getInternalProjectPath(project.gradle), depVersion: project.labkeyVersion)
                    BuildUtils.addLabKeyDependency(project: project, config: "implementation", depProjectPath: BuildUtils.getRemoteApiProjectPath(project.gradle), depVersion: BuildUtils.getLabKeyClientApiVersion(project))
                    if (BuildUtils.isBaseModule(project) && !BuildUtils.isApi(project) && project.findProject(BuildUtils.getApiProjectPath(project.gradle)))
                    {
                        // base modules remove only API dependencies
                        BuildUtils.addLabKeyDependency(project: project, config: "dedupe", depProjectPath: BuildUtils.getApiProjectPath(project.gradle), depProjectConfig: "external")
                    }
                    else // non-base modules remove dependencies from the base modules
                    {
                        for (String path : BuildUtils.getBaseModules(project.gradle))
                        {
                            if ( project.findProject(path) && BuildUtils.shouldBuildFromSource(project.project(path)) && project.project(path).configurations.findByName("external") != null) // exclude dependencies only if building that module (otherwise we don't have the external configuration)
                            {
                                BuildUtils.addLabKeyDependency(project: project, config: "dedupe", depProjectPath: path, depProjectConfig: "external")
                            }
                        }
                    }

                    if (XmlBeans.isApplicable(project))
                        implementation project.tasks.schemasCompile.outputs.files
                    if (Api.isApplicable(project))
                        implementation project.files(project.tasks.apiJar)
                }
    }
}

