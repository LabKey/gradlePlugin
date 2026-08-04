/*
 * Copyright (c) 2017-2026 LabKey Corporation
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

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.api.java.archives.Manifest
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.ModuleExtension
import org.labkey.gradle.plugin.extension.ServerDeployExtension
import org.labkey.gradle.task.DeployModule
import org.labkey.gradle.task.ModuleXmlFile
import org.labkey.gradle.task.UndeployModule
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames
import org.labkey.gradle.util.PomFileHelper
import org.labkey.gradle.util.TaskUtils

/**
 * This class is used for building a LabKey file-based module, which contains only client-side code.
 * It also serves as a base class for the Java module classes.
 */
class FileModule implements Plugin<Project>
{
    public static final Attribute ARTIFACT_TYPE = Attribute.of('artifactType', String)
    public static final String MODULE_ARTIFACT_TYPE = "module"
    public static final String API_JAR_ARTIFACT_TYPE = "apiJar"

    static boolean shouldDoBuild(Project project, boolean logMessages)
    {
        List<String> indicators = new ArrayList<>()
        if (!project.file(ModuleExtension.MODULE_PROPERTIES_FILE).exists())
            indicators.add(ModuleExtension.MODULE_PROPERTIES_FILE + " does not exist")

        if (indicators.size() > 0 && logMessages)
        {
            project.logger.quiet("${project.path} build skipped because: " + indicators.join("; "))
        }
        return indicators.isEmpty()
    }

    @Override
    void apply(Project project)
    {
        def moduleKey = project.getName().toLowerCase()
        def shouldBuild = shouldDoBuild(project, true)
        if (project.findProject(BuildUtils.getServerProjectPath(project.gradle)) != null) {
            ServerDeployExtension deployExt = BuildUtils.getServerProject(project).extensions.getByType(ServerDeployExtension.class)

            def otherPath = deployExt.getFoundModule(moduleKey)

            if (otherPath != null && !otherPath.equals(project.getPath()) && project.findProject(otherPath) != null) {
                if (shouldBuild)
                    throw new IllegalStateException("Found duplicate module '${project.getName()}' in ${project.getPath()} and ${otherPath}. Modules should have unique names; Rename one or exclude it from your build.")
            } else if (shouldBuild) {
                deployExt.addFoundModule(moduleKey, project.getPath())
            }
        }

        if (shouldBuild) {
            project.apply plugin: 'java'
            project.apply plugin: 'org.labkey.build.base'

            project.extensions.create("lkModule", ModuleExtension, project)
            addSourceSet(project)
            applyPlugins(project)
            addConfigurations(project)
            addTasks(project)
            addDependencies(project)
            addArtifacts(project)
        }
    }

    private static void addSourceSet(Project project)
    {
        ModuleResources.addSourceSet(project)
    }

    protected static void applyPlugins(Project project)
    {
        project.apply plugin: 'maven-publish'

        if (SpringConfig.isApplicable(project))
            project.apply plugin: 'org.labkey.build.springConfig'

        if (Webapp.isApplicable(project))
            project.apply plugin: 'org.labkey.build.webapp'

        ClientLibraries.addTasks(project)

        if (NpmRun.isApplicable(project))
            project.apply plugin: 'org.labkey.build.npmRun'

    }

    protected void addConfigurations(Project project)
    {
        project.configurations
                {
                    published {
                        canBeConsumed = true
                        canBeResolved = false
                        attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                        // The second attribute is needed to be able to distinguish from the API jar file when doing dependency substitution for distributions
                        attributes.attribute(ARTIFACT_TYPE, MODULE_ARTIFACT_TYPE)
                    }
                }
    }

    protected void addTasks(Project project)
    {
        ModuleResources.addTasks(project)

        var moduleXmlTask = project.tasks.register('moduleXml', ModuleXmlFile) {
            ModuleXmlFile task ->
                List<String> moduleDependencies = []
                project.configurations.modules.dependencies.each {
                    Dependency dep -> moduleDependencies += dep.getName()
                }
                if (!moduleDependencies.isEmpty())
                    project.lkModule.setPropertyValue("ModuleDependencies", moduleDependencies.join(", "))
                task.getModuleProperties().set(project.lkModule.getModProperties())
                if (project.file("build.gradle").exists())
                    task.inputs.file(project.file("build.gradle"))
                task.outputs.cacheIf { false } // disable build caching. Has too many undeclared inputs.
        }

        var moduleTask = project.tasks.register("module", Jar) {
            Jar jar ->
                jar.group = GroupNames.MODULE
                jar.description = "create the module file for this project"
                jar.from project.labkey.explodedModuleDir
                jar.exclude '**/*.uptodate'
                jar.exclude "META-INF/${project.name}/**"
                jar.archiveBaseName.set(project.name)
                jar.archiveExtension.set('module')
                jar.destinationDirectory.set(project.layout.buildDirectory)
                jar.outputs.cacheIf({true})
        }

        moduleTask.configure {
            it.dependsOn(project.tasks.named('processResources'))
            it.dependsOn(moduleXmlTask)
            setJarManifestAttributes(project, (Manifest) it.manifest)
            if (!LabKeyExtension.isDevMode(project) && BuildUtils.haveMinificationProject(project.gradle))
                it.dependsOn(project.tasks.named('compressClientLibs'))
        }

        project.tasks.named("build").configure {dependsOn(moduleTask)}
        project.tasks.clean {
            dependsOn(project.tasks.named('cleanModule'))
            if (BuildUtils.haveMinificationProject(project.gradle))
                dependsOn(project.tasks.named('cleanClientLibs'))
        }

        project.artifacts
                {
                    published(moduleTask)
                }

        project.tasks.register('deployModule', DeployModule)
            { DeployModule task ->
                task.group = GroupNames.MODULE
                task.description = "copy a project's .module file to the local deploy directory"
                task.moduleFiles.from(moduleTask, project.configurations.modules)
                task.moduleFileName.set(moduleTask.get().archiveFileName)
            }



        project.tasks.register('undeployModule', UndeployModule) {
            UndeployModule task ->
                task.group = GroupNames.MODULE
                task.description = "remove a project's .module file and the unjarred file from the deploy directory"
        }

        project.tasks.register("reallyClean") {
            Task task ->
                task.group = GroupNames.BUILD
                task.description = "Deletes the build, staging, and deployment directories of this module"
                task.dependsOn(project.tasks.clean, project.tasks.undeployModule)
                TaskUtils.addOptionalTaskDependency(project, task, "cleanNodeModules")
                TaskUtils.addOptionalTaskDependency(project, task, "cleanSchemasCompile")
        }
    }

    static void setJarManifestAttributes(Project project, Manifest manifest)
    {
        manifest.attributes(
                "Implementation-Version": project.version,
                "Implementation-Title": project.lkModule.getPropertyValue("Label", project.name),
                "Implementation-Vendor": "LabKey"
        )
    }

    static undeployModule(Project project)
    {
        getModuleFilesAndDirectories(project).forEach({File file ->
            project.delete file
        })
    }


    /**
     * Finds all module files and directories for a project included in the deployment directory and/or staging directory
     * @param project the project to find module files for
     * @param includeDeployed include .module files and directories in the build/deploy directory
     * @param includeStaging include .module files in the build/staging directory
     * @return list of files and directories for this module with the deploy .module files first, followed by the deploy directories
     *          followed by the staging .module files.
     */
    static List<File> getModuleFilesAndDirectories(Project project, Boolean includeDeployed = true, Boolean includeStaging=true)
    {
        return getModuleFilesAndDirectories(
                project.name,
                includeDeployed ? new File(ServerDeployExtension.getModulesDeployDirectory(project)) : null,
                includeStaging ? BuildUtils.getRootBuildDirFile(project, ServerDeploy.STAGING_MODULES_DIR) : null
        )
    }

    /**
     * The same as {@link #getModuleFilesAndDirectories(Project, Boolean, Boolean)} but without any reference to a
     * project, so it can be used from a task action.
     * @param moduleName the name of the module whose files are to be found
     * @param deployDir the deploy directory to look in, or null to skip the deploy directory
     * @param stagingDir the staging directory to look in, or null to skip the staging directory
     * @return list of files and directories for this module with the deploy .module files first, followed by the deploy
     *          directories followed by the staging .module files.
     */
    static List<File> getModuleFilesAndDirectories(String moduleName, File deployDir, File stagingDir)
    {
        String moduleFilePrefix = "${moduleName}-"
        List<File> files = new ArrayList<>()
        if (deployDir != null)
        {
            if (deployDir.isDirectory())
            {
                // first add the files because we want to delete these first.  If the directory goes away and the .module file is there
                // the directory might get recreated because of listeners.
                files.addAll(deployDir.listFiles(new FileFilter() {
                    @Override
                    boolean accept(final File file)
                    {
                        return file.isFile() && file.getName().startsWith(moduleFilePrefix)
                    }
                })
                )

                // then add the directories
                files.addAll(deployDir.listFiles(new FileFilter() {
                    @Override
                    boolean accept(final File file)
                    {
                        return file.isDirectory() && (file.getName().startsWith(moduleFilePrefix) || file.getName().equalsIgnoreCase(moduleName))
                    }
                })
                )
            }
        }
        // staging has only the .modules files
        if (stagingDir != null)
        {
            if (stagingDir.isDirectory())
            {
                files.addAll(stagingDir.listFiles(new FilenameFilter() {
                    @Override
                    boolean accept(final File dir,
                                   final String name)
                    {
                        return name.startsWith(moduleFilePrefix)
                    }
                })
                )
            }
        }
        return files
    }

    protected void addArtifacts(Project project)
    {
        project.afterEvaluate {
            project.publishing {
                publications {
                    if (project.hasProperty('module'))
                    {
                        Properties pomProperties = LabKeyExtension.getModulePomProperties(project)
                        modules(MavenPublication) { pub ->
                            // Use org.labkey.module for module dependency groupIds instead of "org.labkey"
                            pub.groupId = pomProperties.get('groupId')
                            pub.artifact(project.tasks.module)
                            PomFileHelper pomUtil = new PomFileHelper(pomProperties, project, true)
                            pom {
                                name = project.name
                                description = pomProperties.getProperty("Description")
                                url = pomProperties.getProperty("OrganizationURL")
    //                                    developers PomFileHelper.getLabKeyTeamDevelopers()
                                licenses pomUtil.getLicense()
                                organization pomUtil.getOrganization()
    //                                    scm PomFileHelper.getLabKeyScm()
                                withXml {
                                    pomUtil.getDependencyClosure(asNode(), true)
                                }
                            }
                        }
                    }

                    if (project.hasProperty('apiJar'))
                    {
                        Properties pomProperties = LabKeyExtension.getApiPomProperties(project)
                        apiLib(MavenPublication) { pub ->
                            pub.groupId = pomProperties.get('groupId')
                            pub.artifact(project.tasks.apiJar)

                            PomFileHelper pomUtil = new PomFileHelper(pomProperties, project, false)
                            pom {
                                name = project.name
                                description = pomProperties.getProperty("Description")
                                url = pomProperties.getProperty("OrganizationURL")
    //                                    developers PomFileHelper.getLabKeyTeamDevelopers()
                                licenses pomUtil.getLicense()
                                organization pomUtil.getOrganization()
    //                                    scm PomFileHelper.getLabKeyScm()
                                withXml {
                                    pomUtil.getDependencyClosure(asNode(), false)
                                }
                            }

                        }
                    }
                    else if (project.path.equals(BuildUtils.getApiProjectPath(project.gradle)))
                    {
                        Properties pomProperties = LabKeyExtension.getApiPomProperties(project)

                        apiLib(MavenPublication) { pub ->
                            pub.groupId = pomProperties.get('groupId')
                            pub.artifact(project.tasks.jar)

                            PomFileHelper pomUtil = new PomFileHelper(pomProperties, project, false)
                            pom {
                                name = project.name
                                description = pomProperties.getProperty("Description")
                                url = PomFileHelper.LABKEY_ORG_URL
                                developers PomFileHelper.getLabKeyTeamDevelopers()
                                licenses pomUtil.getLicense()
                                organization pomUtil.getOrganization()
    //                                    scm PomFileHelper.getLabKeyScm()
                                withXml {
                                    pomUtil.getDependencyClosure(asNode(), false)
                                }
                            }

                        }
                    }
                }

                if (BuildUtils.shouldPublish(project))
                {
                    if (LabKeyExtension.isDevMode(project))
                        throw new GradleException("Modules produced with deployMode=dev are not portable and should never be published.")
                    project.artifactoryPublish {
                        if (project.hasProperty('module'))
                        {
                            dependsOn project.tasks.named("module")
                        }

                        if (project.hasProperty('apiJar'))
                        {
                            dependsOn project.tasks.named("apiJar")
                        }
                        else if (project.path.equals(BuildUtils.getApiProjectPath(project.gradle)))
                        {
                            dependsOn project.tasks.named("jar")
                        }
                        publications('modules', 'apiLib')
                    }
                }
            }
        }
    }

    private static void addDependencies(Project project)
    {
        Project serverProject = BuildUtils.getServerProject(project)
        if (serverProject != null && serverProject != project)
        {
            project.evaluationDependsOn(BuildUtils.getServerProjectPath(project.gradle))
            // This is done after the project is evaluated otherwise the dependencies for the modules configuration will not have been added yet.
            project.afterEvaluate({
                BuildUtils.addLabKeyDependency(project: serverProject, config: 'modules', depProjectPath: project.path, depProjectConfig: 'published', depExtension: 'module')
                BuildUtils.addLabKeyDependency(project: serverProject, config: 'builtModules', depProjectPath: project.path, depProjectConfig: 'published', depExtension: 'module')
                try {
                    project.configurations.named("modules") {
                        Configuration config -> {
                            config.dependencies.each {
                                Dependency dep ->
                                    if (dep instanceof ProjectDependency) {
                                        ProjectDependency projectDep = (ProjectDependency) dep
                                        if (shouldDoBuild(project.project(projectDep.getPath()), false)) {
                                            BuildUtils.addLabKeyDependency(project: serverProject, config: 'modules', depProjectPath: projectDep.getPath(), depProjectConfig: 'published', depExtension: 'module')
                                            BuildUtils.addLabKeyDependency(project: serverProject, config: 'builtModules', depProjectPath: projectDep.getPath(), depProjectConfig: 'published', depExtension: 'module')
                                        } else {
                                            serverProject.dependencies.add("modules", BuildUtils.getLabKeyArtifactName(project, projectDep.getPath(), projectDep.version, "module"))
                                            serverProject.dependencies.add("downloadedModules", BuildUtils.getLabKeyArtifactName(project, projectDep.getPath(), projectDep.version, "module"))
                                        }
                                    } else {
                                        serverProject.dependencies.add("modules", dep)
                                        serverProject.dependencies.add("downloadedModules", dep)
                                    }
                            }
                        }
                    }

                } catch (UnknownDomainObjectException ignore) { }
            })
        }

    }
}
