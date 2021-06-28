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

import java.util.regex.Matcher

/**
 * This class is used for building a LabKey file-based module, which contains only client-side code.
 * It also serves as a base class for the Java module classes.
 */
class FileModule implements Plugin<Project>
{
    private static final Map<String, String> _foundModules = new HashMap<>();

    @Override
    void apply(Project project)
    {
        def moduleKey = project.getName().toLowerCase()
        def otherPath = _foundModules.get(moduleKey)
        def shouldBuild = shouldDoBuild(project, true)
        if (moduleKey.equals("embedded"))
        {
            if (shouldBuild)
                throw new IllegalStateException("Found module at ${project.getPath()}. \"embedded\" is a reserved name. Rename it or excluded it from your build.")
        }
        if (otherPath != null && !otherPath.equals(project.getPath()) && project.findProject(otherPath) != null)
        {
            if (shouldBuild)
                throw new IllegalStateException("Found duplicate module '${project.getName()}' in ${project.getPath()} and ${otherPath}. Modules should have unique names; Rename one or exclude it from your build.")
        }
        else
        {
            if (shouldBuild)
                _foundModules.put(moduleKey, project.getPath())
            else
                _foundModules.remove(moduleKey)
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


    private void addSourceSet(Project project)
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
        project.tasks.register('moduleXml') {
            Task task ->
                task.doLast {
                    InputStream is = getClass().getClassLoader().getResourceAsStream("module.template.xml")
                    if (is == null)
                    {
                        throw new GradleException("Could not find 'module.template.xml' as resource file")
                    }

                    List<String> moduleDependencies = [];
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
        }

        Task moduleXmlTask = project.tasks.moduleXml
        if (project.file(ModuleExtension.MODULE_PROPERTIES_FILE).exists())
            moduleXmlTask.inputs.file(project.file(ModuleExtension.MODULE_PROPERTIES_FILE))
        else
            project.logger.info("${project.path} - ${ModuleExtension.MODULE_PROPERTIES_FILE} not found so not added as input to 'moduleXml'")
        moduleXmlTask.outputs.file(moduleXmlFile)
        if (project.file("build.gradle").exists())
            moduleXmlTask.inputs.file(project.file("build.gradle"))
        moduleXmlTask.outputs.cacheIf {true} // enable build caching

        // This is added because Intellij started creating this "out" directory when you build through IntelliJ.
        // It copies files there that are actually input files to the build, which causes some problems when later
        // builds attempt to find their input files.
        project.tasks.create("cleanOut", Delete) {
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
            project.tasks.register("module", Jar) {
                Jar jar ->
                    jar.group = GroupNames.MODULE
                    jar.description = "create the module file for this project"
                    jar.from project.labkey.explodedModuleDir
                    jar.exclude '**/*.uptodate'
                    jar.exclude "META-INF/${project.name}/**"
                    jar.exclude 'gwt-unitCache/**'
                    jar.archiveBaseName.set(project.name)
                    jar.archiveExtension.set('module')
                    jar.destinationDirectory = project.buildDir
                    jar.outputs.cacheIf({true})
            }

            Task moduleFile = project.tasks.module

            moduleFile.dependsOn(project.tasks.named('processResources'))
            moduleFile.dependsOn(moduleXmlTask)
            setJarManifestAttributes(project, (Manifest) moduleFile.manifest)
            if (!LabKeyExtension.isDevMode(project))
                moduleFile.dependsOn(project.tasks.named('compressClientLibs'))
            project.tasks.build.dependsOn(moduleFile)
            project.tasks.clean.dependsOn(project.tasks.named('cleanModule'))

            project.artifacts
                    {
                        published moduleFile
                    }

            project.tasks.register('deployModule')
                { Task task ->
                    task.group = GroupNames.MODULE
                    task.description = "copy a project's .module file to the local deploy directory"
                    task.inputs.files moduleFile
                    task.outputs.file "${ServerDeployExtension.getModulesDeployDirectory(project)}/${moduleFile.outputs.getFiles()[0].getName()}"

                    task.doLast {
                        project.copy { CopySpec copy ->
                            copy.from moduleFile
                            copy.from project.configurations.modules
                            copy.into project.staging.modulesDir
                        }
                        project.copy { CopySpec copy ->
                            copy.from moduleFile
                            copy.from project.configurations.modules
                            copy.into ServerDeployExtension.getModulesDeployDirectory(project)
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
                        delete.outputs.dir "${ServerDeployExtension.getServerDeployDirectory(project)}/modules"
                    })
                    task.doFirst {
                        undeployModule(project)
                        undeployJspJar(project)
                        Api.deleteModulesApiJar(project)
                    }
            }


            project.tasks.register("reallyClean") {
                Task task ->
                    task.group = GroupNames.BUILD
                    task.description = "Deletes the build, staging, and deployment directories of this module"
                    task.dependsOn(project.tasks.clean, project.tasks.undeployModule)
                    if (project.tasks.findByName('cleanNodeModules') != null)
                        task.dependsOn(project.tasks.cleanNodeModules)
                    if (project.tasks.findByName('cleanSchemasCompile') != null)
                        task.dependsOn(project.tasks.cleanSchemasCompile)
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

    static undeployJspJar(Project project)
    {
        File jspDir = new File("${project.rootProject.buildDir}/deploy/labkeyWebapp/WEB-INF/jsp")
        if (jspDir.isDirectory())
        {
            List<File> files = new ArrayList<>()
            files.addAll(jspDir.listFiles(new FileFilter() {
                @Override
                boolean accept(final File file)
                {
                    return file.isFile() && file.getName().startsWith("${project.tasks.module.baseName}_jsp");
                }
            })
            )
            files.each {
                File file -> project.delete file
            }
        }
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
                        else if (project.path.equals(BuildUtils.getApiProjectPath(project.gradle))
                                || project.path.equals(BuildUtils.getInternalProjectPath(project.gradle)))
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
                                dependsOn project.tasks.module
                            }

                            if (project.hasProperty('apiJar'))
                            {
                                dependsOn project.tasks.apiJar
                            }
                            else if (project.path.equals(BuildUtils.getApiProjectPath(project.gradle))
                                    || project.path.equals(BuildUtils.getInternalProjectPath(project.gradle)))
                            {
                                dependsOn project.tasks.jar
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
