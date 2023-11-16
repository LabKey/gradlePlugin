/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.java.archives.Manifest
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Jar
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.ModuleExtension
import org.labkey.gradle.plugin.extension.ServerDeployExtension
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames
import org.labkey.gradle.util.PomFileHelper
import org.labkey.gradle.util.PropertiesUtils
import org.labkey.gradle.util.TaskUtils

import java.util.regex.Matcher

/**
 * This class is used for building a LabKey file-based module, which contains only client-side code.
 * It also serves as a base class for the Java module classes.
 */
class FileModule implements Plugin<Project>
{
    static boolean shouldDoBuild(Project project, boolean logMessages)
    {
        List<String> indicators = new ArrayList<>()
        if (!project.file(ModuleExtension.MODULE_PROPERTIES_FILE).exists())
            indicators.add(ModuleExtension.MODULE_PROPERTIES_FILE + " does not exist")
        if (project.hasProperty("skipBuild"))
            indicators.add("skipBuild property set for Gradle project")

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


        if (AntBuild.isApplicable(project))
        {
            project.apply plugin: 'org.labkey.build.antBuild'
        }
        else
        {
            if (SpringConfig.isApplicable(project))
                project.apply plugin: 'org.labkey.build.springConfig'

            if (Webapp.isApplicable(project))
                project.apply plugin: 'org.labkey.build.webapp'

            ClientLibraries.addTasks(project)

            if (NpmRun.isApplicable(project))
                project.apply plugin: 'org.labkey.build.npmRun'
        }
    }

    protected void addConfigurations(Project project)
    {
        project.configurations
                {
                    published
                }
    }

    protected void addTasks(Project project)
    {
        ModuleResources.addTasks(project)

        File moduleXmlFile = new File("${project.labkey.explodedModuleConfigDir}/module.xml")
        var moduleXmlTask = project.tasks.register('moduleXml') {
            Task task ->
                task.doLast {
                    InputStream is = getClass().getClassLoader().getResourceAsStream("module.template.xml")
                    if (is == null)
                    {
                        throw new GradleException("Could not find 'module.template.xml' as resource file")
                    }

                    List<String> moduleDependencies = []
                    project.configurations.modules.dependencies.each {
                        Dependency dep -> moduleDependencies += dep.getName()
                    }
                    if (!moduleDependencies.isEmpty())
                        project.lkModule.setPropertyValue(ModuleExtension.MODULE_DEPENDENCIES_PROPERTY, moduleDependencies.join(", "))
                    project.mkdir(project.labkey.explodedModuleConfigDir)
                    OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(moduleXmlFile))

                    is.readLines().each {
                        String line ->
                            Matcher matcher = PropertiesUtils.PROPERTY_PATTERN.matcher(line)
                            String newLine = line
                            while (matcher.find())
                            {
                                newLine = newLine.replace(matcher.group(), (String) project.lkModule.getPropertyValue(matcher.group(1), ""))
                            }
                            writer.println(newLine)
                    }
                    writer.close()
                    is.close()
                }

                if (project.file(ModuleExtension.MODULE_PROPERTIES_FILE).exists())
                    task.inputs.file(project.file(ModuleExtension.MODULE_PROPERTIES_FILE))
                else
                    project.logger.info("${project.path} - ${ModuleExtension.MODULE_PROPERTIES_FILE} not found so not added as input to 'moduleXml'")
                task.outputs.file(moduleXmlFile)
                if (project.file("build.gradle").exists())
                    task.inputs.file(project.file("build.gradle"))
                task.outputs.cacheIf { false } // disable build caching. Has too many undeclared inputs.
        }

        // This is added because Intellij started creating this "out" directory when you build through IntelliJ.
        // It copies files there that are actually input files to the build, which causes some problems when later
        // builds attempt to find their input files.
        project.tasks.register("cleanOut", Delete) {
            Delete task ->
                task.group = GroupNames.BUILD
                task.description = "removes the ${project.file('out')} directory created by Intellij builds"
                task.configure({ Delete delete ->
                    if (project.file("out").isDirectory())
                        project.delete project.file("out")
                })
        }

        if (!AntBuild.isApplicable(project))
        {
            var moduleTask = project.tasks.register("module", Jar) {
                Jar jar ->
                    jar.group = GroupNames.MODULE
                    jar.description = "create the module file for this project"
                    jar.from project.labkey.explodedModuleDir
                    jar.exclude '**/*.uptodate'
                    jar.exclude "META-INF/${project.name}/**"
                    jar.exclude 'gwt-unitCache/**'
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
                        // TODO: Figure out how to add this artifact without resolving 'module' task
                        published moduleTask.get()
                    }

            project.tasks.register('deployModule')
                { Task task ->
                    task.group = GroupNames.MODULE
                    task.description = "copy a project's .module file to the local deploy directory"
                    task.inputs.files moduleTask
                    task.outputs.file "${ServerDeployExtension.getModulesDeployDirectory(project)}/${moduleTask.get().outputs.getFiles()[0].getName()}"

                    task.doLast {
                        project.copy { CopySpec copy ->
                            copy.from moduleTask
                            copy.from project.configurations.modules
                            copy.into project.staging.modulesDir
                            copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
                        }
                        project.copy { CopySpec copy ->
                            copy.from moduleTask
                            copy.from project.configurations.modules
                            copy.into ServerDeployExtension.getModulesDeployDirectory(project)
                            copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
                        }
                    }
                }

            project.tasks.register('undeployModule', Delete) {
                Delete task ->
                    task.group = GroupNames.MODULE
                    task.description = "remove a project's .module file and the unjarred file from the deploy directory"
                    task.configure(
                    { Delete delete ->
                        getModuleFilesAndDirectories(project).forEach({
                            File file ->
                                if (file.isDirectory())
                                    delete.inputs.dir file
                                else
                                    delete.inputs.file file
                        })
                    })
                    task.doFirst {
                        undeployModule(project)
                        Api.deleteModulesApiJar(project)
                    }
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
        String moduleFilePrefix = "${project.name}-"
        List<File> files = new ArrayList<>()
        if (includeDeployed)
        {
            File deployDir = new File(ServerDeployExtension.getModulesDeployDirectory(project))
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
                        return file.isDirectory() && (file.getName().startsWith("${project.name}-") || file.getName().equalsIgnoreCase(project.name))
                    }
                })
                )
            }
        }
        // staging has only the .modules files
        if (includeStaging)
        {
            File stagingDir = new File((String) project.staging.modulesDir)
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
        if (!AntBuild.isApplicable(project))
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
                if (project.configurations.findByName("modules") != null)
                    project.configurations.modules.dependencies.each {
                        Dependency dep ->
                            if (dep instanceof ProjectDependency)
                            {
                                ProjectDependency projectDep = (ProjectDependency) dep
                                if (shouldDoBuild(projectDep.dependencyProject, false)) {
                                    BuildUtils.addLabKeyDependency(project: serverProject, config: 'modules', depProjectPath: projectDep.dependencyProject.getPath(), depProjectConfig: 'published', depExtension: 'module')
                                }
                                else {
                                    serverProject.dependencies.add("modules", BuildUtils.getLabKeyArtifactName(project, projectDep.dependencyProject.getPath(), projectDep.version, "module"))
                                }
                            }
                            else
                            {
                                serverProject.dependencies.add("modules", dep)
                            }
                    }
            })
        }

    }
}
