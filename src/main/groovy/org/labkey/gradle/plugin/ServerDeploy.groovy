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
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
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
    public static final List<String> JDBC_JARS = ["jtds.jar", "mysql.jar", "postgresql.jar"]
    public static final List<String> TOMCAT_LIB_UNVERSIONED_JARS = JDBC_JARS + ["ant.jar", "mail.jar", "javax.activation.jar"]

    private ServerDeployExtension serverDeploy

    @Override
    void apply(Project project)
    {
        serverDeploy = project.extensions.create("serverDeploy", ServerDeployExtension)

        serverDeploy.dir = ServerDeployExtension.getServerDeployDirectory(project)
        serverDeploy.embeddedDir = ServerDeployExtension.getEmbeddedServerDeployDirectory(project)
        serverDeploy.modulesDir = "${serverDeploy.dir}/modules"
        serverDeploy.webappDir = "${serverDeploy.dir}/labkeyWebapp"
        serverDeploy.binDir = "${serverDeploy.dir}/bin"
        serverDeploy.rootWebappsDir = BuildUtils.getWebappConfigPath(project)
        serverDeploy.pipelineLibDir = "${serverDeploy.dir}/pipelineLib"

        project.apply plugin: 'org.labkey.build.base'
        // we depend on the jar task from the embedded project, if available
        if (BuildUtils.useEmbeddedTomcat(project))
            project.evaluationDependsOn(BuildUtils.getEmbeddedProjectPath(project.gradle))

        addTasks(project)
    }

    private void addTasks(Project project)
    {
        project.tasks.register(
                "deployApp", DeployApp) {
            DeployApp task ->
                task.group = GroupNames.DEPLOY
                task.description = "Deploy the application locally into ${serverDeploy.dir}"
        }

        StagingExtension staging = project.getExtensions().getByType(StagingExtension.class)

        // The staging step complicates things, but it is currently needed for the following reasons:
        // - We want to make sure tomcat doesn't restart multiple times when deploying the application.
        //   (seems like it could be avoided as the copy being done here is just as atomic as the copy from deployModules)
        project.tasks.register("stageModules") {
            Task task ->
                task.group = GroupNames.DEPLOY
                task.description = "Stage the modules for the application into ${staging.dir}"
                task.doFirst({
                    project.delete staging.modulesDir
                })
                task.doLast( {
                    // copy over the module dependencies first (things not built from source that might bring in
                    // transitive dependencies)
                    FileCollection remoteModules = project.configurations.modules.fileCollection ({
                        Dependency dependency -> dependency instanceof DefaultExternalModuleDependency
                    })
                    if (!remoteModules.isEmpty())
                    {
                        project.ant.copy(
                                todir: staging.modulesDir,
                                preserveLastModified: true // this is important so we don't re-explode modules that have not changed
                        )
                                {
                                    remoteModules.addToAntBuilder(project.ant, "fileset", FileCollection.AntType.FileSet)
                                }
                    }

                    // Then copy over the project dependencies (things built from source) so they will replace
                    // any transitive dependencies that were brought in).
                    // One might like to do this overriding/overwriting using DependencySubstitution, as that is very much
                    // what it is designed for, but that allows substitution of a project for an ExternalModuleDependency
                    // and since a .module file is only one of the artifacts produced by our projects (e.g., :server:modules:platform:experiment)
                    // and is not the default artifact, DependencySubstitution does not seem to work.
                    FileCollection localModules = project.configurations.modules.fileCollection ({
                        Dependency dependency -> dependency instanceof DefaultProjectDependency
                    })
                    if (!localModules.isEmpty())
                    {
                        project.ant.copy(
                                overwrite: true, // overwrite existing files even if the destination files are newer
                                todir: staging.modulesDir,
                                preserveLastModified: true // this is important so we don't re-explode modules that have not changed
                        )
                                {
                                    localModules.addToAntBuilder(project.ant, "fileset", FileCollection.AntType.FileSet)
                                }
                    }

                })
        }
        project.tasks.stageModules.dependsOn project.configurations.modules


        project.tasks.register("checkModuleVersions", CheckForVersionConflicts) {
            CheckForVersionConflicts task ->
                task.directory = new File(serverDeploy.modulesDir)
                task.extension = "module"
                task.cleanTask = ":server:cleanDeploy"
                task.collection = project.configurations.modules
                task.group =  GroupNames.DEPLOY
                task.description = "Check for conflicts in version numbers of module files to be deployed and files in the deploy directory. " +
                        "Default action on detecting a conflict is to fail.  Use -PversionConflictAction=[delete|fail|warn] to change this behavior.  The value 'delete' will cause the " +
                        "conflicting version(s) in the ${serverDeploy.modulesDir} directory to be removed."
            }


        project.tasks.stageModules.dependsOn(project.tasks.checkModuleVersions)


        project.tasks.register("checkVersionConflicts") {
            Task task ->
                task.group = GroupNames.DEPLOY
                task.description = "Check for conflicts in version numbers on module files and jar files in modules."
                task.dependsOn(project.tasks.checkModuleVersions)
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
                        // we'll need to support both yarn and npm, so link them both if both are present.
                        if (project.hasProperty('npmVersion') && project.hasProperty('npmWorkDirectory'))
                            linkBinaries(project, "npm", project.npmVersion, project.npmWorkDirectory)
                        if (project.hasProperty('yarnVersion') && project.hasProperty('yarnWorkDirectory'))
                            linkBinaries(project, "yarn", project.yarnVersion, project.yarnWorkDirectory)
                    })
            }
            project.tasks.deployApp.dependsOn(project.tasks.symlinkNode)
        }


        project.tasks.register(
                "stageRemotePipelineJars") {
            Task task ->
                task.group = GroupNames.DEPLOY
                task.description = "Copy files needed for using remote pipeline jobs into ${staging.pipelineLibDir}"
                task.doLast(
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
        }

        project.tasks.stageRemotePipelineJars.dependsOn project.configurations.remotePipelineJars

        project.tasks.register(
                "stageApp") {
            Task task ->
                task.group = GroupNames.DEPLOY
                task.description = "Stage modules and jar files into ${staging.dir}"
                task.dependsOn project.tasks.stageModules
                task.dependsOn project.tasks.stageRemotePipelineJars
        }

        project.tasks.register(
                "setup",  DoThenSetup) {
            DoThenSetup task ->
                task.group = GroupNames.DEPLOY
                task.description = "Installs labkey.xml and various jar files into the tomcat directory.  Sets default database properties."
                // stage the application first to try to avoid multiple Tomcat restarts
                task.mustRunAfter(project.tasks.stageApp)
        }

        project.tasks.deployApp.dependsOn(project.tasks.setup)
        project.tasks.deployApp.dependsOn(project.tasks.stageApp)

        if (BuildUtils.useEmbeddedTomcat(project)) {
            def embeddedProject = project.project(BuildUtils.getEmbeddedProjectPath())

            project.tasks.register("cleanEmbeddedDeploy", DefaultTask) {
                DefaultTask task ->
                    task.group = GroupNames.DEPLOY
                    task.description = "Remove the ${project.serverDeploy.embeddedDir} directory"
                    task.doLast {
                        project.delete project.serverDeploy.embeddedDir
                    }
            }
            project.tasks.deployApp.dependsOn(project.tasks.cleanEmbeddedDeploy)
            project.tasks.stageApp.dependsOn(embeddedProject.tasks.build)
            project.tasks.setup.mustRunAfter(project.tasks.cleanEmbeddedDeploy)
            project.tasks.deployApp.doLast({
                project.copy {
                    CopySpec copy ->
                        copy.from new File(embeddedProject.buildDir, "libs")
                        copy.into project.serverDeploy.embeddedDir
                }
            })
            project.tasks.register("deployEmbeddedDistribution", DeployEmbeddedDistribution) {
                DeployEmbeddedDistribution task ->
                    task.group = GroupNames.DISTRIBUTION
                    task.description = "Extract the executable jar from a distribution and put it and the included binaries in the appropriate deploy directory"
                    task.dependsOn(project.tasks.cleanEmbeddedDeploy, project.tasks.setup)
            }
        }

        String log4jFile = project.hasProperty('log4j2Version') ? 'log4j2.xml' : 'log4j.xml'
        project.tasks.register('configureLog4j', ConfigureLog4J) {
            ConfigureLog4J task ->
                task.fileName = log4jFile
                task.group = GroupNames.DEPLOY
                task.description = "Edit and copy ${log4jFile} file"
        }
        project.tasks.stageApp.dependsOn(project.tasks.configureLog4j)

        project.tasks.register("stageDistribution", StageDistribution) {
            StageDistribution task ->
                task.group = GroupNames.DISTRIBUTION
                task.description = "Populate the staging directory using a LabKey distribution file from directory dist or directory specified with distDir property. Use property distType to specify zip or tar.gz (default)."
        }

        project.tasks.register("deployDistribution", DeployApp) {
            DeployApp task ->
                task.group = GroupNames.DISTRIBUTION
                task.description = "Deploy a LabKey distribution file from directory dist or directory specified with distDir property.  Use property distType to specify zip or tar.gz (default)."
                task.dependsOn(project.tasks.stageDistribution, project.tasks.configureLog4j, project.tasks.setup)
        }



        // This may prevent multiple Tomcat restarts
        project.tasks.setup.mustRunAfter(project.tasks.stageDistribution)
        project.tasks.configureLog4j.mustRunAfter(project.tasks.stageDistribution)

        project.tasks.register('undeployModules',UndeployModules) {
            UndeployModules task ->
                task.group = GroupNames.DEPLOY
                task.description = "Removes all module files and directories from the deploy and staging directories"
        }


        project.tasks.register(
                'cleanStaging',Delete) {
            Delete task ->
                task.group = GroupNames.DEPLOY
                task.description = "Removes the staging directory ${staging.dir}"
                task.configure({ DeleteSpec spec ->
                    spec.delete staging.dir
                })
        }

        project.tasks.register(
                'cleanDeploy', Delete) {
            Delete task ->
                task.group = GroupNames.DEPLOY
                task.description = "Removes the deploy directory ${serverDeploy.dir}"
                task.dependsOn (project.tasks.stopTomcat, project.tasks.cleanStaging)
                task.configure({ DeleteSpec spec ->
                    spec.delete serverDeploy.dir
                })
        }

        project.tasks.register("cleanTomcatLib") {
            Task task ->
                task.group = GroupNames.DEPLOY
                task.description = "Remove the jar files deployed to the tomcat/lib directory"
                task.doLast {
                    deleteTomcatLibs(project)
                }
        }


        project.tasks.register("cleanAndDeploy", DeployApp) {
            DeployApp task ->
                task.group = GroupNames.DEPLOY
                task.description = "Removes the deploy directory ${serverDeploy.dir} then deploys the application locally"
                task.dependsOn(project.tasks.cleanDeploy)
        }

        project.tasks.register("cleanBuild", Delete) {
            Delete task ->
                task.group = GroupNames.DEPLOY
                task.description = "Remove the build directory ${project.rootProject.buildDir}"
                task.dependsOn (project.tasks.stopTomcat)
                task.configure({ DeleteSpec spec ->
                    spec.delete project.rootProject.buildDir
                })
        }


    }

    private linkBinaries(Project project, String packageMgr, String version, workDirectory) {

        Project pmLinkProject = project.findProject(BuildUtils.getNodeBinProjectPath(project.gradle))
        if (pmLinkProject == null)
            return

        File linkContainer = new File("${project.rootDir}/${project.npmWorkDirectory}")
        linkContainer.mkdirs()

        Path pmLinkPath = Paths.get("${linkContainer.getPath()}/${packageMgr}")
        String pmDirName = "${packageMgr}-v${version}"
        Path pmTargetPath = Paths.get("${pmLinkProject.buildDir}/${workDirectory}/${pmDirName}")

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
            File nodeDir = new File("${pmLinkProject.buildDir}/${project.nodeWorkDirectory}")
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

    private static void deleteTomcatLibs(Project project)
    {
        project.tomcat.validateCatalinaHome()

        Files.newDirectoryStream(Paths.get(project.tomcat.catalinaHome, "lib"), "${BuildUtils.BOOTSTRAP_JAR_BASE_NAME}*.jar").each { Path path ->
            project.delete path.toString()
        }

        project.configurations.tomcatJars.files.each {File jarFile ->
            File libFile = new File("${project.tomcat.catalinaHome}/lib/${jarFile.getName()}")
            if (libFile.exists())
                project.delete libFile.getAbsolutePath()
        }

        // Get rid of (un-versioned) jars that were deployed
        TOMCAT_LIB_UNVERSIONED_JARS.each{String name ->
            File libFile = new File("${project.tomcat.catalinaHome}/lib/${name}")
            if (libFile.exists())
                project.delete libFile.getAbsolutePath()
        }
    }
}



