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
    public static final String CLASSIFIER = "api"
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
        project.apply plugin: 'maven'
        project.apply plugin: 'maven-publish'
        addSourceSet(project)
        addDependencies(project)
        addApiJarTask(project)
        addArtifacts(project)
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

    private void addDependencies(Project project)
    {
        project.dependencies
                {
                    BuildUtils.addLabKeyDependency(project: project, config: 'apiCompile', depProjectPath: ":server:api")
                    BuildUtils.addLabKeyDependency(project: project, config: 'apiCompile', depProjectPath: ":server:internal")
                    BuildUtils.addLabKeyDependency(project: project, config: "apiCompile", depProjectPath: ":remoteapi:java", depVersion: project.labkeyVersion)
                }
    }

    private static void addApiJarTask(Project project)
    {
        Task apiJar = project.task("apiJar",
                group: GroupNames.API,
                type: Jar,
                description: "produce jar file for api",
                {Jar jar ->
                    jar.classifier CLASSIFIER
                    jar.from project.sourceSets.api.output
                    jar.baseName = "${project.name}_api"
                    jar.destinationDir = project.file(project.labkey.explodedModuleLibDir)
                })
        project.tasks.processApiResources.enabled = false
        apiJar.dependsOn(project.apiClasses)
        if (project.hasProperty('jsp2Java'))
            project.tasks.jsp2Java.dependsOn(apiJar)

        project.tasks.assemble.dependsOn(apiJar)

        if (LabKeyExtension.isDevMode(project))
        {
            // we put all API jar files into a special directory for the RecompilingJspClassLoader's classpath
            apiJar.doLast {
                project.copy { CopySpec copy ->
                    copy.from project.file(project.labkey.explodedModuleLibDir)
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
                    apiCompile project.tasks.apiJar
                }
    }
}
