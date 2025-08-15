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
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Delete
import org.labkey.gradle.plugin.extension.ServerDeployExtension
import org.labkey.gradle.task.*
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames
import org.labkey.gradle.util.TaskUtils

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * First stages then deploys the application locally to the tomcat directory
 */
class ServerDeploy implements Plugin<Project>
{
    public static final String DEPLOY_DIR = "deploy"
    public static final String MODULES_DIR = "modules"
    public static final String DEPLOY_MODULES_DIR = "${DEPLOY_DIR}/${MODULES_DIR}"
    public static final String DEPLOY_WEBAPP_DIR = "${DEPLOY_DIR}/labkeyWebapp"
    public static final String DEPLOY_PIPELINE_DIR = "${DEPLOY_DIR}/pipelineLib"
    public static final String DEPLOY_BIN_DIR = "${DEPLOY_DIR}/bin"
    public static final String STAGING_DIR = "staging"
    public static final String STAGING_MODULES_DIR = "${STAGING_DIR}/modules/"
    public static final String STAGING_PIPELINE_DIR = "${STAGING_DIR}/pipelineLib"

    private ServerDeployExtension serverDeploy
    String deployDir
    String embeddedDir
    String stagingDir

    @Override
    void apply(Project project)
    {
        serverDeploy = project.extensions.create("serverDeploy", ServerDeployExtension)

        deployDir = ServerDeployExtension.getServerDeployDirectoryPath(project)
        embeddedDir = ServerDeployExtension.getEmbeddedServerDeployDirectory(project).getAsFile().getAbsolutePath()
        stagingDir = BuildUtils.getRootBuildDirFile(project, STAGING_DIR)

        project.apply plugin: 'org.labkey.build.base'
        // we depend on the jar task from the embedded project, if available
        if (BuildUtils.embeddedProjectExists(project))
            project.evaluationDependsOn(BuildUtils.getEmbeddedProjectPath(project.gradle))

        addTasks(project)
        addConfigurations(project)
    }

    private void addConfigurations(project)
    {
        project.configurations
                {
                    builtModules
                    downloadedModules
                }

    }

    private void addTasks(Project project)
    {


        project.tasks.register("stageModules", StageModules) {
            StageModules task ->
                task.group = GroupNames.DEPLOY
                task.description = "Stage the modules for the application into ${stagingDir}"
                task.downloadedModules.setFrom(project.configurations.downloadedModules)
                task.builtModules.setFrom(project.configurations.builtModules)
        }

        project.tasks.register("checkModuleVersions", CheckForVersionConflicts) {
            CheckForVersionConflicts task ->
                String modulesDir = "${deployDir}/${MODULES_DIR}"
                task.directory = new File(modulesDir)
                task.extension = "module"
                task.cleanTask = ":server:cleanDeploy"
                task.collection = project.configurations.modules
                task.group =  GroupNames.DEPLOY
                task.description = "Check for conflicts in version numbers of module files to be deployed and files in the deploy directory. " +
                        "Default action on detecting a conflict is to fail.  Use -PversionConflictAction=[delete|fail|warn] to change this behavior.  The value 'delete' will cause the " +
                        "conflicting version(s) in the ${modulesDir} directory to be removed."
                task.onlyIf({
                    return new File(modulesDir).exists()
                })
            }

        project.tasks.named('stageModules').configure {dependsOn(project.tasks.checkModuleVersions)}

        project.tasks.register("checkVersionConflicts") {
            Task task ->
                task.group = GroupNames.DEPLOY
                task.description = "Check for conflicts in version numbers on module files and jar files in modules."
                task.dependsOn(project.tasks.checkModuleVersions)
        }


        String stagingPipelineLibDir = BuildUtils.getRootBuildDirFile(project, STAGING_PIPELINE_DIR)

        project.tasks.register("stageRemotePipelineJars") {
            Task task ->
                task.group = GroupNames.DEPLOY
                task.description = "Copy files needed for using remote pipeline jobs into ${stagingPipelineLibDir}"
                task.inputs.files(project.configurations.remotePipelineJars.getFiles())
                task.outputs.dir(BuildUtils.getRootBuildDirFile(project, STAGING_PIPELINE_DIR))
                task.doLast({
                    if (!inputs.files.isEmpty()) {
                        ant.copy(
                            todir: outputs.files.singleFile,
                            preserveLastModified: true
                        )
                        {
                            inputs.files.addToAntBuilder(ant, "fileset", FileCollection.AntType.FileSet)
                        }
                    }
                })
        }

        project.tasks.named('stageRemotePipelineJars').configure {
            it.dependsOn project.configurations.remotePipelineJars
        }

        project.tasks.register(
                "stageApp") {
            Task task ->
                task.group = GroupNames.DEPLOY
                task.description = "Stage modules and jar files into ${stagingDir}"
                task.dependsOn project.tasks.stageModules
                task.dependsOn project.tasks.stageRemotePipelineJars
        }

        project.tasks.register("deployApp", DeployApp) {
            DeployApp task ->
                task.group = GroupNames.DEPLOY
                task.description = "Deploy the application locally into ${deployDir}"
                task.binaries.setFrom(project.configurations.binaries)
                task.bootJar.setFrom(project.project(BuildUtils.getEmbeddedProjectPath()).tasks.bootJar)
                task.driverFiles.setFrom(project.configurations.driver)
                // stage the application first to try to avoid multiple Tomcat restarts
                task.dependsOn(project.tasks.stageApp)
        }

        // Creating symbolic links on Windows requires elevated permissions.  Even with these permissions, the createSymbolicLink method fails
        // with a message "required privilege is not held by the client".  Using the ant.symlink task "succeeds", but it causes .sys files to be created in the
        // .node directory, which are not symbolic links and will cause failures with a message "java.nio.file.NotLinkException: The file or directory is not a reparse point."
        // the next time the command is run.
        //
        // So, for now, for Windows users, the symbolic links can be created manually using the following command when running as an administrator
        //   MKLINK /D <npmLinkPath> <npmTargetPath>
        // or users can skip the link creation and put the target of the link in their path instead.
        //
        // At a later date, we can possibly make this task execute the mklink command (and its counterpart to remove the link).
        // This would require that the gradle tasks be run as an administrator, and that is possibly not ideal.
        if (!SystemUtils.IS_OS_WINDOWS &&  project.hasProperty('nodeVersion')) {
            project.tasks.register("symlinkNode") {
                Task task ->
                    task.group = GroupNames.DEPLOY
                    task.description = "Make a symbolic link to the npm directory for use in PATH environment variable"
                    task.doFirst({
                        if (project.hasProperty('npmVersion') && project.hasProperty('npmWorkDirectory'))
                            linkBinaries(project, "npm", project.npmVersion, project.npmWorkDirectory)
                    })
                    task.dependsOn(project.tasks.npmSetup)
                    task.notCompatibleWithConfigurationCache("Needs its own class to declare proper input and output properties")
            }
            project.tasks.symlinkNode.notCompatibleWithConfigurationCache("References project properties. Need to add task class with input properties")
            project.tasks.named('deployApp').configure {dependsOn(project.tasks.symlinkNode)}
        }


        if (BuildUtils.embeddedProjectExists(project)) {
            def embeddedProject = project.project(BuildUtils.getEmbeddedProjectPath())

            project.tasks.register("cleanEmbeddedDeploy", Delete) {
                Delete task ->
                    task.group = GroupNames.DEPLOY
                    task.description = "Remove the ${embeddedDir} directory"
                    task.configure { DeleteSpec delete ->
                        delete.delete embeddedDir
                    }
            }
            project.tasks.named('deployApp').configure {Task task ->
                task.mustRunAfter(project.tasks.cleanEmbeddedDeploy)
            }
            TaskUtils.getOptionalTask(embeddedProject, 'checkVersionConflicts').ifPresent(task -> {
                project.tasks.named('deployApp').configure {it.dependsOn(task)}
            })

            project.tasks.named('stageApp').configure {it.dependsOn(embeddedProject.tasks.build)}

        }

        project.tasks.register("stageDistribution", StageDistribution) {
            StageDistribution task ->
                task.group = GroupNames.DISTRIBUTION
                task.description = "Populate the staging directory using a LabKey distribution file from directory dist or directory specified with distDir property."
        }

        project.tasks.register("deployDistribution", DeployDistribution) {
            DeployDistribution task ->
                task.group = GroupNames.DISTRIBUTION
                task.description = "Extract the executable jar from a distribution and put it and the included binaries in the appropriate deploy directory"
                task.dependsOn(project.tasks.cleanEmbeddedDeploy)
                task.binaries.setFrom(project.configurations.binaries)
        }

        project.tasks.register('undeployModules', UndeployModules) {
            UndeployModules task ->
                task.group = GroupNames.DEPLOY
                task.description = "Removes all module files and directories from the deploy and staging directories"
                task.notCompatibleWithConfigurationCache("Walks the project tree")
        }

        project.tasks.register(
                'cleanStaging', Delete) {
            Delete task ->
                task.group = GroupNames.DEPLOY
                task.description = "Removes the staging directory ${stagingDir}"
                task.configure({ DeleteSpec spec ->
                    spec.delete stagingDir
                })
        }

        project.tasks.register(
                'cleanDeploy', Delete) {
            Delete task ->
                task.group = GroupNames.DEPLOY
                task.description = "Removes the deploy directory ${deployDir}"
                task.dependsOn (project.tasks.cleanStaging)
                task.configure({ DeleteSpec spec ->
                    spec.delete deployDir
                })
        }
        project.tasks.named('deployApp').configure {mustRunAfter(project.tasks.cleanDeploy)}

        project.tasks.register("cleanBuild", Delete) {
            Delete task ->
                task.group = GroupNames.DEPLOY
                task.description = "Remove the build directory ${project.rootProject.layout.buildDirectory}"
                task.configure({ DeleteSpec spec ->
                    spec.delete project.rootProject.layout.buildDirectory
                })
        }
        project.tasks.named('deployApp').configure {it.mustRunAfter(project.tasks.cleanBuild)}

        project.tasks.named("cleanBuild").configure {
            it.dependsOn(project.tasks.stopLabKey)
            it.mustRunAfter(project.tasks.stopTomcat)
        }
        project.tasks.named("cleanDeploy").configure {
            it.dependsOn(project.tasks.stopLabKey)
            it.mustRunAfter(project.tasks.stopTomcat)
        }
    }

    private static linkBinaries(Project project, String packageMgr, String version, workDirectory) {

        Project pmLinkProject = project.findProject(BuildUtils.getNodeBinProjectPath(project.gradle))
        if (pmLinkProject == null)
            return

        File linkContainer = new File("${project.rootDir}/${project.npmWorkDirectory}")
        linkContainer.mkdirs()

        Path pmLinkPath = Paths.get("${linkContainer.getPath()}/${packageMgr}")
        String pmDirName = "${packageMgr}-v${version}"
        Path pmTargetPath = Paths.get(pmLinkProject.file( "${workDirectory}/${pmDirName}").getPath())

        if (!Files.isSymbolicLink(pmLinkPath) || !Files.readSymbolicLink(pmLinkPath).getFileName().toString().equals(pmDirName))
        {
            // if the symbolic link exists, we want to replace it
            if (Files.isSymbolicLink(pmLinkPath))
                Files.delete(pmLinkPath)

            Files.createSymbolicLink(pmLinkPath, pmTargetPath)
        }

        String nodeFilePrefix = "node-v${project.nodeVersion}-"
        Path nodeLinkPath = Paths.get("${linkContainer.getPath()}/node")
        if (!Files.isSymbolicLink(nodeLinkPath) || !Files.readSymbolicLink(nodeLinkPath).getFileName().toString().startsWith(nodeFilePrefix))
        {
            File nodeDir = pmLinkProject.file(project.nodeWorkDirectory)
            File[] nodeFiles = nodeDir.listFiles({ File file -> file.name.startsWith(nodeFilePrefix) } as FileFilter)
            if (nodeFiles != null && nodeFiles.length > 0)
            {
                // if the symbolic link exists, we want to replace it
                if (Files.isSymbolicLink(nodeLinkPath))
                    Files.delete(nodeLinkPath)

                Files.createSymbolicLink(nodeLinkPath, nodeFiles[0].toPath())
            }
            else
                project.logger.warn("No file found with prefix ${nodeDir.path}/${nodeFilePrefix}.  Symbolic link in ${linkContainer.getPath()}/node not created.")
        }
    }
}
