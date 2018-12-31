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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Delete
import org.labkey.gradle.plugin.extension.ServerDeployExtension
import org.labkey.gradle.plugin.extension.StagingExtension
import org.labkey.gradle.task.*
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
/**
 * First stages then deploys the application locally to the tomcat directory
 */
class ServerDeploy implements Plugin<Project>
{
    public static final List<String> TOMCAT_LIB_UNVERSIONED_JARS = ["ant.jar", "mail.jar", "jtds.jar", "mysql.jar", "postgresql.jar", "javax.activation.jar"]

    private ServerDeployExtension serverDeploy

    @Override
    void apply(Project project)
    {
        serverDeploy = project.extensions.create("serverDeploy", ServerDeployExtension)

        serverDeploy.dir = ServerDeployExtension.getServerDeployDirectory(project)
        serverDeploy.modulesDir = "${serverDeploy.dir}/modules"
        serverDeploy.webappDir = "${serverDeploy.dir}/labkeyWebapp"
        serverDeploy.binDir = "${serverDeploy.dir}/bin"
        serverDeploy.rootWebappsDir = "${project.rootDir}/webapps"
        serverDeploy.pipelineLibDir = "${serverDeploy.dir}/pipelineLib"
        addTasks(project)
    }

    private void addTasks(Project project)
    {
        Task deployAppTask = project.task(
                "deployApp",
                group: GroupNames.DEPLOY,
                type: DeployApp,
                description: "Deploy the application locally into ${serverDeploy.dir}"
        )

        StagingExtension staging = project.getExtensions().getByType(StagingExtension.class)

        // The staging step complicates things, but it is currently needed for the following reasons:
        // - we currently depend on it for generating the apiFilesList that determines which libraries to keep and which to remove from WEB-INF/lib
        //   (could be accomplished with a sync task perhaps)
        // - We need to put certain libraries in WEB-INF/lib because the RecompilingJspClassLoader uses that in its classpath
        // - We want to make sure tomcat doesn't restart multiple times when deploying the application.
        //   (seems like it could be avoided as the copy being done here is just as atomic as the copy from deployModules)
        Task stageModulesTask = project.task(
                "stageModules",
                group: GroupNames.DEPLOY,
                description: "Stage the modules for the application into ${staging.dir}",
        ).doFirst (
                {
                    project.delete staging.modulesDir
                }
        ).doLast({
            project.ant.copy(
                    todir: staging.modulesDir,
                    preserveLastModified: true // this is important so we don't re-explode modules that have not changed
            )
                    {
                        project.configurations.modules { Configuration collection ->
                            collection.addToAntBuilder(project.ant, "fileset", FileCollection.AntType.FileSet)
                        }
                    }
        })
        stageModulesTask.dependsOn project.configurations.modules


        Task checkModuleVersionsTask = project.task(
                "checkModuleVersions",
                group: GroupNames.DEPLOY,
                description: "Check for conflicts in version numbers of module files to be deployed and files in the deploy directory. " +
                        "Default action on detecting a conflict is to fail.  Use -PversionConflictAction=[delete|fail|warn] to change this behavior.  The value 'delete' will cause the " +
                        "conflicting version(s) in the ${serverDeploy.modulesDir} directory to be removed.",
                type: CheckForVersionConflicts,
                { CheckForVersionConflicts task ->
                    task.directory = new File(serverDeploy.modulesDir)
                    task.extension = "module"
                    task.cleanTask = ":server:cleanDeploy"
                    task.collection = project.configurations.modules
                }
        )

        stageModulesTask.dependsOn(checkModuleVersionsTask)


        Task stageJarsTask = project.task(
                "stageJars",
                group: GroupNames.DEPLOY,
                description: "Stage select jars into ${staging.dir}"
        ).doFirst({
            project.delete staging.libDir
        }).doLast({
                project.ant.copy(
                        todir: staging.libDir,
                        preserveLastModified: true
                )
                        {
                            project.configurations.jars { Configuration collection ->
                                collection.addToAntBuilder(project.ant, "fileset", FileCollection.AntType.FileSet)
                            }
                        }
            })

        stageJarsTask.dependsOn project.configurations.jars
        Task checkJarsTask = project.task(
                "checkWebInfLibJarVersions",
                group: GroupNames.DEPLOY,
                description: "Check for conflicts in version numbers of jar files to be deployed to and files in the directory ${serverDeploy.webappDir}/WEB-INF/lib." +
                        "Default action on detecting a conflict is to fail.  Use -PversionConflictAction=[delete|fail|warn] to change this behavior.  The value 'delete' will cause the " +
                        "conflicting version(s) in the ${serverDeploy.webappDir}/WEB-INF/lib directory to be removed.",
                type: CheckForVersionConflicts,
                { CheckForVersionConflicts task ->
                    task.directory = new File("${serverDeploy.webappDir}/WEB-INF/lib")
                    task.extension = "jar"
                    task.cleanTask = ":server:cleanDeploy"
                    task.collection = project.configurations.jars
                }
        )
        stageJarsTask.dependsOn(checkJarsTask)

        project.task(
                "checkVersionConflicts",
                group: GroupNames.DEPLOY,
                description: "Check for conflicts in version numbers on module files, WEB-INF/lib jar files and jar files in modules."
        ).dependsOn(checkModuleVersionsTask, checkJarsTask)

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
        if (!SystemUtils.IS_OS_WINDOWS && project.hasProperty('npmVersion') && project.hasProperty('nodeVersion')) {
            project.task("symlinkNode",
                    group: GroupNames.DEPLOY,
                    description: "Make a symbolic link to the npm directory for use in PATH environment variable").doFirst( {
                File linkContainer = new File("${project.rootDir}/${project.npmWorkDirectory}")
                linkContainer.mkdirs()

                // default to core project path for backward compatibility.  npmSetup introduced for :server in 18.3
                Project npmLinkProject = project.project(BuildUtils.getNodeBinProjectPath(project.gradle))
                Path npmLinkPath = Paths.get("${linkContainer.getPath()}/npm")
                String npmDirName = "npm-v${project.npmVersion}"
                Path npmTargetPath = Paths.get("${npmLinkProject.buildDir}/${project.npmWorkDirectory}/${npmDirName}")
                if (!Files.isSymbolicLink(npmLinkPath) || !Files.readSymbolicLink(npmLinkPath).getFileName().toString().equals(npmDirName))
                {
                    // if the symbolic link exists, we want to replace it
                    if (Files.isSymbolicLink(npmLinkPath))
                        Files.delete(npmLinkPath)

                    Files.createSymbolicLink(npmLinkPath, npmTargetPath)
                }

                String nodeFilePrefix = "node-v${project.nodeVersion}-"
                Path nodeLinkPath = Paths.get("${linkContainer.getPath()}/node")
                if (!Files.isSymbolicLink(nodeLinkPath) || !Files.readSymbolicLink(nodeLinkPath).getFileName().toString().startsWith(nodeFilePrefix))
                {
                    File nodeDir = new File("${npmLinkProject.buildDir}/${project.nodeWorkDirectory}")
                    File[] nodeFiles  = nodeDir.listFiles({ File file -> file.name.startsWith(nodeFilePrefix) } as FileFilter )
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
            })
            project.tasks.deployApp.dependsOn(project.tasks.symlinkNode)
        }


        Task stageRemotePipelineJarsTask = project.task(
                "stageRemotePipelineJars",
                group: GroupNames.DEPLOY,
                description: "Copy files needed for using remote pipeline jobs into ${staging.pipelineLibDir}"
        ).doLast(
                {
                    project.ant.copy(
                            todir: staging.pipelineLibDir,
                            preserveLastModified: true
                    )
                            {
                                project.configurations.remotePipelineJars { Configuration collection ->
                                    collection.addToAntBuilder(project.ant, "fileset", FileCollection.AntType.FileSet)

                                }
                            }
                }
        )

        stageRemotePipelineJarsTask.dependsOn project.configurations.remotePipelineJars

        project.task(
                "stageApp",
                group: GroupNames.DEPLOY,
                description: "Stage modules and jar files into ${staging.dir}",
                { Task task ->
                    task.dependsOn stageModulesTask
                    task.dependsOn stageJarsTask
                    task.dependsOn stageRemotePipelineJarsTask
                }
        )
        deployAppTask.dependsOn(project.tasks.stageApp)

        Task setup = project.task(
                "setup",
                group: GroupNames.DEPLOY,
                type: DoThenSetup,
                description: "Installs labkey.xml and various jar files into the tomcat directory.  Sets default database properties."
        )
        deployAppTask.dependsOn(setup)
        // stage the application first to try to avoid multiple Tomcat restarts
        setup.mustRunAfter(project.tasks.stageApp)

        Task log4jTask = project.task(
                'configureLog4j',
                group: GroupNames.DEPLOY,
                type: ConfigureLog4J,
                description: "Edit and copy log4j.xml file",
        )
        project.tasks.stageApp.dependsOn(log4jTask)

        project.task(
                "stageDistribution",
                group: GroupNames.DISTRIBUTION,
                description: "Populate the staging directory using a LabKey distribution file from build/dist or directory specified with distDir property. Use property distType to specify zip or tar.gz (default).",
                type: StageDistribution
        )

        Task deployDistTask = project.task(
                "deployDistribution",
                type: DeployApp,
                group: GroupNames.DISTRIBUTION,
                description: "Deploy a LabKey distribution file from build/dist or directory specified with distDir property.  Use property distType to specify zip or tar.gz (default).",
        )
        deployDistTask.dependsOn(project.tasks.stageDistribution)
        deployDistTask.dependsOn(log4jTask)
        deployDistTask.dependsOn(setup)
        // This may prevent multiple Tomcat restarts
        setup.mustRunAfter(project.tasks.stageDistribution)
        log4jTask.mustRunAfter(project.tasks.stageDistribution)

        project.task(
                'undeployModules',
                group: GroupNames.DEPLOY,
                description: "Removes all module files and directories from the deploy and staging directories",
                type: UndeployModules
        )

        project.task(
                'cleanStaging',
                group: GroupNames.DEPLOY,
                type: Delete,
                description: "Removes the staging directory ${staging.dir}",
                { DeleteSpec spec ->
                    spec.delete staging.dir
                }
        )

        project.task(
                'cleanDeploy',
                group: GroupNames.DEPLOY,
                type: Delete,
                description: "Removes the deploy directory ${serverDeploy.dir}",
                dependsOn: [project.tasks.stopTomcat, project.tasks.cleanStaging],
                { DeleteSpec spec ->
                    spec.delete serverDeploy.dir
                }
        )

        project.task(
                "cleanTomcatLib",
                group: GroupNames.DEPLOY,
                description: "Remove the jar files deployed to the tomcat/lib directory"
        ).doLast {
            deleteTomcatLibs(project)
        }

        project.task(
                "cleanAndDeploy",
                group: GroupNames.DEPLOY,
                type: DeployApp,
                description: "Removes the deploy directory ${serverDeploy.dir} then deploys the application locally",
                dependsOn: project.tasks.cleanDeploy
        )

        project.task(
                "cleanBuild",
                group: GroupNames.DEPLOY,
                type: Delete,
                description: "Remove the build directory ${project.rootProject.buildDir}",
                dependsOn: project.tasks.stopTomcat,
                { DeleteSpec spec ->
                    spec.delete project.rootProject.buildDir
                }
        )

    }

    private static void deleteTomcatLibs(Project project)
    {
        Files.newDirectoryStream(Paths.get(project.tomcatDir, "lib"), "${ServerBootstrap.JAR_BASE_NAME}*.jar").each { Path path ->
            project.delete path.toString()
        }

        project.configurations.tomcatJars.files.each {File jarFile ->
            File libFile = new File("${project.tomcatDir}/lib/${jarFile.getName()}")
            if (libFile.exists())
                project.delete libFile.getAbsolutePath()
        }

        // Get rid of (un-versioned) jars that were deployed
        TOMCAT_LIB_UNVERSIONED_JARS.each{String name ->
            File libFile = new File("${project.tomcatDir}/lib/${name}")
            if (libFile.exists())
                project.delete libFile.getAbsolutePath()
        }
    }
}



