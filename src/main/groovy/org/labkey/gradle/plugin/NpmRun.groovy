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
package org.labkey.gradle.plugin

import org.apache.commons.lang3.SystemUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DeleteSpec
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.PathSensitivity
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
    public static final String UI_COMPONENTS = "labkey-ui-components"
    public static final String UI_PREMIUM = "labkey-ui-premium"
    public static final String JS_API = "labkey-api-js"
    public static final String CLIENT_API_DIR = "clientAPIs"

    private static final String EXTENSION_NAME = "npmRun"
    private static final String BUILD_CLIENT_LIBS_TASK = "npmRunBuildClientLibs"
    private static final String NODE_PLUGIN_ID = "com.github.node-gradle.node"
    private static final String NPM_TASK_CLASS = "com.github.gradle.node.npm.task.NpmTask"

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
        project.apply plugin: NODE_PLUGIN_ID
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

            // Base URL for fetching node distributions (change if you have a mirror).
            if (project.hasProperty('nodeRepo'))
                distBaseUrl = project.nodeRepo

            if (BuildUtils.useServerNode(project)) {
                // The directory where Node.js is unpacked (when download is true)
                workDir =  project.file("${project.rootProject.projectDir}/.node")

                // The directory where npm is installed (when a specific version is defined)
                npmWorkDir = project.file("${project.rootProject.projectDir}/.node")
            }

            // If true, it will download node using above parameters.
            // If false, it will try to use globally installed node.
            download = (BuildUtils.useOwnNode(project) || project.path.equals(BuildUtils.getServerProjectPath(project.gradle)))
                    && project.hasProperty('nodeVersion') && project.hasProperty('npmVersion')

            // Set the work directory where node_modules should be located
            nodeProjectDir = project.file("${project.projectDir}")

            npmInstallCommand = project.hasProperty('npmInstallCommand') ? project.npmInstallCommand : 'ci'
        }
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
        configureBuildTask(project.tasks.named("npm_run_${project.npmRun.buildProd}"))

        def npmRunBuild = project.tasks.register("npmRunBuild")
                {Task task ->
                    task.group = GroupNames.NPM_RUN
                    task.description ="Runs 'npm run ${project.npmRun.buildDev}'"
                    task.dependsOn "npm_run_${project.npmRun.buildDev}"
                    task.mustRunAfter "npmInstall"
                }

        def npmRunBuildDevTask = project.tasks.named("npm_run_${project.npmRun.buildDev}")
        configureBuildTask(npmRunBuildDevTask)
        if (project.hasProperty("runClientLibBuilds")) {
            List<File> clientLibDirs = getClientLibDirs(project)
            TaskProvider<Task> clientLibsTask = getClientLibsBuildTask(project, clientLibDirs)
            if (clientLibsTask != null)
                npmRunBuildDevTask.configure { Task task ->
                    task.dependsOn(clientLibsTask)
                    // Rebuild this module when the library build outputs change
                    task.inputs.files(clientLibDirs.collect { new File(it, "dist") })
                            .withPropertyName("clientLibs")
                            .withPathSensitivity(PathSensitivity.RELATIVE)
                }
        }
        if (BuildUtils.useServerNode(project) && project.path !== BuildUtils.getServerProject(project).path) {
            project.tasks.named('npmSetup').configure
                    {
                        Task task ->
                            task.dependsOn(BuildUtils.getServerProject(project).tasks.npmSetup)
                    }
        }

        project.tasks.named('npmInstall').configure
                {Task task ->
                    // Specify legacy peer dependency mode for npm v7+
                    task.args = ["--legacy-peer-deps"]
                }

        def runCommand = LabKeyExtension.isDevMode(project) && !project.hasProperty('useNpmProd') ? npmRunBuild : npmRunBuildProd
        TaskUtils.configureTaskIfPresent(project, "module", { dependsOn(runCommand) })
        TaskUtils.configureTaskIfPresent(project, "processResources", { dependsOn(runCommand) })
        TaskUtils.configureTaskIfPresent(project, "processModuleResources", { dependsOn(runCommand) })
        TaskUtils.configureTaskIfPresent(project, "processWebappResources", { dependsOn(runCommand) })
    }


    /**
     * @return the client library enlistments under clientAPIs, in dependency order
     */
    private static List<File> getClientLibDirs(Project project)
    {
        return [getJSAPIDir(project), getUIComponentsDir(project), getUIPremiumDir(project)].findAll { it != null }
    }

    /**
     * Returns the root project's task for building the client library enlistments under clientAPIs, registering
     * it if this is the first module to ask for it. This way the libraries are built once per build, not once per module.
     * The builds themselves are NpmTasks registered in the first module so they use the npm and node from that module's
     * node configuration, which the root project doesn't have.
     * @return the task provider, or null if there are no client library enlistments
     */
    private static TaskProvider<Task> getClientLibsBuildTask(Project project, List<File> clientLibDirs)
    {
        if (clientLibDirs.isEmpty())
            return null

        Project rootProject = project.rootProject
        if (rootProject.tasks.names.contains(BUILD_CLIENT_LIBS_TASK))
            return rootProject.tasks.named(BUILD_CLIENT_LIBS_TASK)

        // The node plugin is loaded by the build script, which this plugin's classloader can't see, so get NpmTask from the plugin's classloader
        Class<? extends Task> npmTaskClass = project.plugins.getPlugin(NODE_PLUGIN_ID).class.classLoader.loadClass(NPM_TASK_CLASS) as Class<? extends Task>
        List<TaskProvider<Task>> libTasks = []
        clientLibDirs.each { File libDir ->
            List<TaskProvider<Task>> previousLibTasks = new ArrayList<>(libTasks)
            TaskProvider<Task> installTask = project.tasks.register(getTaskNameFromDirName("npmInstall", libDir.name), npmTaskClass) { Task task ->
                task.group = GroupNames.NPM_RUN
                task.description = "Runs 'npm install --legacy-peer-deps' in ${libDir}"
                task.workingDir.set(libDir)
                // Specify legacy peer dependency mode for npm v7+
                task.args.set(["install", "--legacy-peer-deps"])
                task.inputs.files(new File(libDir, NPM_PROJECT_FILE), new File(libDir, NPM_PROJECT_LOCK_FILE))
                        .withPropertyName("clientLibPackageFiles")
                        .withPathSensitivity(PathSensitivity.RELATIVE)
                // npm v7+ writes this file on every install; tracking it avoids snapshotting all of node_modules
                task.outputs.file(new File(libDir, "node_modules/.package-lock.json"))
                        .withPropertyName("clientLibNodeModules")
            }
            libTasks.add(project.tasks.register(getTaskNameFromDirName("npmRunBuild", libDir.name), npmTaskClass) { Task task ->
                task.group = GroupNames.NPM_RUN
                task.description = "Runs 'npm run build' in ${libDir}"
                task.workingDir.set(libDir)
                task.args.set(["run", "build"])
                task.dependsOn(installTask)
                task.dependsOn(previousLibTasks)
                task.inputs.files(project.fileTree(dir: libDir, includes: ["src/**/*", NPM_PROJECT_FILE, NPM_PROJECT_LOCK_FILE, TYPESCRIPT_CONFIG_FILE, "package.config.js", "webpack.config.js"]))
                        .withPropertyName("clientLibSources")
                        .withPathSensitivity(PathSensitivity.RELATIVE)
                task.outputs.dir(new File(libDir, "dist"))
                        .withPropertyName("clientLibDist")
            })
        }

        return rootProject.tasks.register(BUILD_CLIENT_LIBS_TASK) { Task task ->
            task.group = GroupNames.NPM_RUN
            task.description = "Runs 'npm install' and 'npm run build' in the client library enlistments under ${CLIENT_API_DIR}"
            task.dependsOn(libTasks)
        }
    }

    private static String getTaskNameFromDirName(String prefix, String dirName)
    {
        switch (dirName) {
            case JS_API: return prefix + "_api"
            case UI_PREMIUM: return prefix + "_premium"
            default: return prefix + "_" + dirName
        }
    }

    static File getUIComponentsDir(Project project)
    {
       return getClientLibsDir(project, UI_COMPONENTS + "/packages/components")
    }

    static File getUIPremiumDir(Project project)
    {
        return getClientLibsDir(project, UI_PREMIUM)
    }

    static File getJSAPIDir(Project project)
    {
        return getClientLibsDir(project, JS_API)
    }

    static File getClientLibsDir(Project project, String clientLibName) {
        File file = new File(project.rootProject.rootDir, "${CLIENT_API_DIR}/${clientLibName}")
        if (file.exists())
            return file
        return null
    }

    private static void addTasks(Project project)
    {
        if (project.file(NPM_PROJECT_FILE).exists())
        {
            addNpmTasks(project)

            project.tasks.register("cleanNodeModules", Delete) {
                Delete task ->
                    task.group = GroupNames.NPM_RUN
                    task.description = "Removes ${project.file(NODE_MODULES_DIR)}"
                    task.configure({ DeleteSpec delete ->
                        if (project.file(NODE_MODULES_DIR).exists())
                            delete.delete(project.file(NODE_MODULES_DIR))
                    })
                    task.mustRunAfter(project.tasks.npmRunClean)
            }
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

