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

import org.apache.commons.lang3.SystemUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DeleteSpec
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.NpmRunExtension
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames
import org.labkey.gradle.util.TaskUtils

/**
 * Used to add tasks for running npm commands for a module.
 *
 * Borrowed heavily from https://plugins.gradle.org/plugin/com.palantir.npm-run
 */
class NpmRun implements Plugin<Project>
{
    public static final String NPM_PROJECT_FILE = "package.json"
    public static final String NPM_PROJECT_LOCK_FILE = "package-lock.json"
    public static final String TYPESCRIPT_CONFIG_FILE = "tsconfig.json"
    public static final String NODE_MODULES_DIR = "node_modules"
    public static final String WEBPACK_DIR = "webpack"
    public static final String ENTRY_POINTS_FILE = "src/client/entryPoints.js"

    private static final String EXTENSION_NAME = "npmRun"

    static boolean isApplicable(Project project)
    {
        return project.file(NPM_PROJECT_FILE).exists()
    }

    static String getNpmCommand()
    {
        return SystemUtils.IS_OS_WINDOWS ? "npm.cmd" : "npm"
    }

    @Override
    void apply(Project project)
    {
        // This brings in nodeSetup and npmInstall tasks.  See https://github.com/node-gradle/gradle-node-plugin
        project.apply plugin: 'com.github.node-gradle.node'
        project.extensions.create(EXTENSION_NAME, NpmRunExtension)

        configurePlugin(project)
        project.afterEvaluate {
            addTasks(project)
        }
    }

    private void configurePlugin(Project project)
    {
        project.node {
            if (project.hasProperty('nodeVersion'))
                // Version of node to use.
                version = project.nodeVersion

            if (project.hasProperty('npmVersion'))
                // Version of npm to use.
                npmVersion = project.npmVersion

            if (project.hasProperty('yarnVersion'))
                // Version of Yarn to use.
                yarnVersion = project.yarnVersion

            // Base URL for fetching node distributions (change if you have a mirror).
            if (project.hasProperty('nodeRepo'))
                distBaseUrl = project.nodeRepo

            // If true, it will download node using above parameters.
            // If false, it will try to use globally installed node.
            download = project.hasProperty('nodeVersion') && project.hasProperty('npmVersion')

            // Set the work directory where node_modules should be located
            nodeModulesDir = project.file("${project.projectDir}")

            npmInstallCommand = project.hasProperty('npmInstallCommand') ? project.npmInstallCommand : 'ci'
        }
    }

    private static void addYarnTasks(Project project)
    {
        project.tasks.register("yarnRunClean")
                {Task task ->
                    task.group = GroupNames.YARN
                    task.description = "Runs 'yarn run ${project.npmRun.clean}'"
                    task.dependsOn "yarn_run_${project.npmRun.clean}"
                }
        TaskUtils.configureTaskIfPresent(project, 'clean', { dependsOn(project.tasks.yarnRunClean) })

        def yarnRunBuildProd = project.tasks.register("yarnRunBuildProd")
                {Task task ->
                    task.group = GroupNames.YARN
                    task.description = "Runs 'yarn run ${project.npmRun.buildProd}'"
                    task.dependsOn "yarn_install"
                    task.dependsOn "yarn_run_${project.npmRun.buildProd}"
                    task.mustRunAfter "yarn_install"
                }
        configureBuildTask(project.tasks.named('yarnRunBuildProd'))
        configureBuildTask(project.tasks.named("yarn_run_${project.npmRun.buildProd}"))

        def yarnRunBuild = project.tasks.register("yarnRunBuild")
                {Task task ->
                    task.group = GroupNames.YARN
                    task.description ="Runs 'yarn run ${project.npmRun.buildDev}'"
                    task.dependsOn "yarn_install"
                    task.dependsOn "yarn_run_${project.npmRun.buildDev}"
                    task.mustRunAfter "yarn_install"
                }
        configureBuildTask(project.tasks.named('yarnRunBuild'))
        configureBuildTask(project.tasks.named("yarn_run_${project.npmRun.buildDev}"))

        def runCommand = LabKeyExtension.isDevMode(project) ? yarnRunBuild : yarnRunBuildProd
        TaskUtils.configureTaskIfPresent(project, "module", { dependsOn(runCommand) })
        TaskUtils.configureTaskIfPresent(project, "processResources", { dependsOn(runCommand) })
        TaskUtils.configureTaskIfPresent(project, "processModuleResources", { dependsOn(runCommand) })
        TaskUtils.configureTaskIfPresent(project, "processWebappResources", { dependsOn(runCommand) })

        project.tasks.named("yarn_install").configure {Task task ->
            task.inputs.file project.file(NPM_PROJECT_FILE)
            if (project.file(NPM_PROJECT_LOCK_FILE).exists())
                task.inputs.file project.file(NPM_PROJECT_LOCK_FILE)
        }
        project.tasks.named("yarn_install").configure {outputs.upToDateWhen { project.file(NODE_MODULES_DIR).exists() } }
    }

    private static void addNpmTasks(Project project)
    {
        project.tasks.register("npmRunClean")
                {Task task ->
                    task.group = GroupNames.NPM_RUN
                    task.description = "Runs 'npm run ${project.npmRun.clean}'"
                    task.dependsOn "npm_run_${project.npmRun.clean}"
                }
        TaskUtils.configureTaskIfPresent(project, 'clean', { dependsOn(project.tasks.npmRunClean) } )

        def npmRunBuildProd = project.tasks.register("npmRunBuildProd")
                {Task task ->
                    task.group = GroupNames.NPM_RUN
                    task.description = "Runs 'npm run ${project.npmRun.buildProd}'"
                    task.dependsOn "npm_run_${project.npmRun.buildProd}"
                    task.mustRunAfter "npmInstall"

                }
        configureBuildTask(project.tasks.named('npmRunBuildProd'))
        configureBuildTask(project.tasks.named("npm_run_${project.npmRun.buildProd}"))

        def npmRunBuild = project.tasks.register("npmRunBuild")
                {Task task ->
                    task.group = GroupNames.NPM_RUN
                    task.description ="Runs 'npm run ${project.npmRun.buildDev}'"
                    task.dependsOn "npm_run_${project.npmRun.buildDev}"
                    task.mustRunAfter "npmInstall"
                }

        configureBuildTask(project.tasks.named('npmRunBuild'))
        configureBuildTask(project.tasks.named("npm_run_${project.npmRun.buildDev}"))

        project.tasks.named('npmInstall').configure
                {Task task ->
                    task.inputs.file project.file(NPM_PROJECT_FILE)
                    if (project.file(NPM_PROJECT_LOCK_FILE).exists())
                        task.inputs.file project.file(NPM_PROJECT_LOCK_FILE)
                    // Specify legacy peer dependency mode for npm v7+
                    task.args = ["--legacy-peer-deps"]
                    task.outputs.upToDateWhen { project.file(NODE_MODULES_DIR).exists() }
                }

        def runCommand = LabKeyExtension.isDevMode(project) && !project.hasProperty('useNpmProd') ? npmRunBuild : npmRunBuildProd
        TaskUtils.configureTaskIfPresent(project, "module", { dependsOn(runCommand) })
        TaskUtils.configureTaskIfPresent(project, "processResources", { dependsOn(runCommand) })
        TaskUtils.configureTaskIfPresent(project, "processModuleResources", { dependsOn(runCommand) })
        TaskUtils.configureTaskIfPresent(project, "processWebappResources", { dependsOn(runCommand) })
    }

    static boolean useYarn(Project project)
    {
        return project.hasProperty("yarnVersion") && !project.file(NPM_PROJECT_LOCK_FILE).exists()
    }

    private static void addTasks(Project project)
    {
        if (project.file(NPM_PROJECT_FILE).exists())
        {
            if (useYarn(project))
                addYarnTasks(project)
            else
                addNpmTasks(project)

            project.tasks.register("cleanNodeModules", Delete) {
                Delete task ->
                    task.group = GroupNames.NPM_RUN
                    task.description = "Removes ${project.file(NODE_MODULES_DIR)}"
                    task.configure({ DeleteSpec delete ->
                        if (project.file(NODE_MODULES_DIR).exists())
                            delete.delete(project.file(NODE_MODULES_DIR))
                    })
                    if (useYarn(project))
                        task.mustRunAfter(project.tasks.yarnRunClean)
                    else
                        task.mustRunAfter(project.tasks.npmRunClean)
            }
        }

        project.tasks.register("listNodeProjects") {
            Task task ->
                task.group = GroupNames.NPM_RUN
                task.description = "List all projects that employ node in their build"
                task.doLast({
                    List<String> nodeProjects = []
                    project.allprojects({Project p ->
                        if (p.getPlugins().hasPlugin(NpmRun.class))
                            nodeProjects.add("${p.path} (${useYarn(p) ? 'yarn' : 'npm'})")
                    })
                    if (nodeProjects.size() == 0)
                        println("No projects found containing ${NPM_PROJECT_FILE}")
                    else {
                        println("The following projects use Node in their builds:\n\t${nodeProjects.join("\n\t")}\n")
                    }
                })
                task.notCompatibleWithConfigurationCache("Needs to walk the project tree")
        }

    }

    private static void configureBuildTask(TaskProvider tp)
    {
        tp.configure { Task task ->
            if (task.project.file(NPM_PROJECT_FILE).exists())
                task.inputs.file task.project.file(NPM_PROJECT_FILE)
            if (task.project.file(TYPESCRIPT_CONFIG_FILE).exists())
                task.inputs.file task.project.file(TYPESCRIPT_CONFIG_FILE)
            if (task.project.file(WEBPACK_DIR).exists())
                task.inputs.dir task.project.file(WEBPACK_DIR)
            if (task.project.file(ENTRY_POINTS_FILE).exists())
                task.inputs.files task.project.file(ENTRY_POINTS_FILE)

            // common input file pattern for client source
            task.inputs.files task.project.fileTree(dir: "src", includes: ["client/**/*", "theme/**/*"])

            // common output file pattern for client artifacts
            task.outputs.dir task.project.file("resources/web/${task.project.name}/gen")
            task.outputs.dir task.project.file("resources/web/gen")
            task.outputs.dir task.project.file("resources/views/gen")
            if (task.project.path.equals(BuildUtils.getPlatformModuleProjectPath(task.project.gradle, "core"))) {
                task.outputs.dir task.project.file("resources/web/clientapi")
                task.outputs.dir task.project.file("resources/web/${task.project.name}/css")
            }

            task.outputs.cacheIf({ true })

            task.usesService(task.project.npmRun.npmRunLimit)
        }
    }
}

