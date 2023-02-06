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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.bundling.Jar
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames

/**
 * Add a sourceSet to create a module's api jar file
 */
class Api implements Plugin<Project>
{
    public static final String SOURCE_DIR = "api-src"
    public static final String ALT_SOURCE_DIR = "src/api-src"
    private static final String MODULES_API_DIR = "modules-api"

    static boolean isApplicable(Project project)
    {
        return project.file(SOURCE_DIR).exists() || project.file(ALT_SOURCE_DIR).exists()
    }

    @Override
    void apply(Project project)
    {
        project.apply plugin: 'java-base'
        addConfigurations(project)
        addSourceSet(project)
        addDependencies(project)
        addApiJarTask(project)
        addArtifacts(project)
    }

    private void addConfigurations(Project project)
    {
        project.configurations {
            apiJarFile // used by other project to declare dependencies to this project api jar
        }
        project.configurations.apiJarFile.setDescription("Configuration that depends on the task that generates the api jar file.  Projects that depend on this project's api jar file should use this configuration in their dependency declaration.")
    }

    private void addSourceSet(Project project)
    {
        project.sourceSets
                {
                    api {
                        java {
                            srcDirs = [project.file(SOURCE_DIR).exists() ? SOURCE_DIR : ALT_SOURCE_DIR, 'internal/gwtsrc']
                        }
                    }
                }
    }

    private static void addDependencies(Project project)
    {
        project.dependencies
                {
                    BuildUtils.addLabKeyDependency(project: project, config: 'apiImplementation', depProjectPath: BuildUtils.getApiProjectPath(project.gradle), depVersion: project.labkeyVersion)
                    BuildUtils.addLabKeyDependency(project: project, config: "apiImplementation", depProjectPath: BuildUtils.getRemoteApiProjectPath(project.gradle), depVersion: BuildUtils.getLabKeyClientApiVersion(project))
                }
    }

    private static void addApiJarTask(Project project)
    {
         project.tasks.register("apiJar", Jar) {
             Jar jar ->
                 jar.group = GroupNames.API
                 jar.description = "produce jar file for api"
                 jar.from project.sourceSets.api.output
                 jar.archiveBaseName.set("${project.name}_api")
                 jar.dependsOn(project.apiClasses)
                 jar.outputs.cacheIf({true})
         }

        project.tasks.processApiResources.enabled = false
        if (project.hasProperty('jsp2Java'))
            project.tasks.jsp2Java.dependsOn(project.tasks.apiJar)

        project.tasks.assemble.dependsOn(project.tasks.apiJar)

        if (LabKeyExtension.isDevMode(project))
        {
            // we put all API jar files into a special directory for the RecompilingJspClassLoader's classpath
            project.tasks.apiJar.doLast {
                project.copy { CopySpec copy ->
                    copy.from project.tasks.apiJar.outputs
                    copy.into "${project.rootProject.buildDir}/${MODULES_API_DIR}"
                    copy.include "${project.name}_api*.jar"
                }
            }
        }
    }

    // It may seem proper to make this action a dependency on the project's clean task since the
    // jar file is put there by the build task, but since the copy is more of a deployment
    // task than a build task and removing it will affect the running server, we make this
    // deletion a step for the 'undeployModule' task instead
    static void deleteModulesApiJar(Project project)
    {
        project.delete project.fileTree("${project.rootProject.buildDir}/${MODULES_API_DIR}") {include "**/${project.name}_api*.jar"}
    }

    private void addArtifacts(Project project)
    {
        project.artifacts
                {
                    apiJarFile project.tasks.apiJar
                }
    }
}
