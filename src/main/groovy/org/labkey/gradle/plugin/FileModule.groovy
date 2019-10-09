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
import org.gradle.api.tasks.bundling.Zip
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.ModuleExtension
import org.labkey.gradle.plugin.extension.ServerDeployExtension
import org.labkey.gradle.task.PomFile
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames
import org.labkey.gradle.util.PropertiesUtils

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * This class is used for building a LabKey file-based module, which contains only client-side code.
 * It also serves as a base class for the Java module classes.
 */
class FileModule implements Plugin<Project>
{
    // Deprecated: instead of creating the skipBuild.txt file,
    // set the skipBuild property to true in the module's build.gradle file
    //   ext.skipBuild = true
    private static final String SKIP_BUILD_FILE = "skipBuild.txt"
    private static final Map<String, String> _foundModules = new HashMap<>();

    @Override
    void apply(Project project)
    {
        def moduleKey = project.getName().toLowerCase()
        def otherPath = _foundModules.get(moduleKey)
        if (otherPath != null && !otherPath.equals(project.getPath()) && project.findProject(otherPath) != null)
        {
            if (_shouldDoBuild(project, false))
                throw new IllegalStateException("Found duplicate module '${project.getName()}' in ${project.getPath()} and ${otherPath}. Modules should have unique names; Rename one or exclude it from your build.")
        }
        else
        {
            if (_shouldDoBuild(project, false))
                _foundModules.put(moduleKey, project.getPath())
            else
                _foundModules.remove(moduleKey)
        }

        project.apply plugin: 'java-base'

        project.build.onlyIf ({
            return shouldDoBuild(project)
        })

        project.extensions.create("lkModule", ModuleExtension, project)
        applyPlugins(project)
        addConfigurations(project)
        addTasks(project)
        addDependencies(project)
        addArtifacts(project)
    }

    static boolean shouldDoBuild(Project project)
    {
        return _shouldDoBuild(project, true)
    }

    private static boolean _shouldDoBuild(Project project, boolean logMessages)
    {
        List<String> indicators = new ArrayList<>()
        if (project.file(SKIP_BUILD_FILE).exists())
            indicators.add(SKIP_BUILD_FILE + " exists")
        if (!project.file(ModuleExtension.MODULE_PROPERTIES_FILE).exists())
            indicators.add(ModuleExtension.MODULE_PROPERTIES_FILE + " does not exist")
        if (project.labkey.skipBuild)
            indicators.add("skipBuild property set for Gradle project")

        if (indicators.size() > 0 && logMessages)
        {
            project.logger.info("$project.name build skipped because: " + indicators.join("; "))
        }
        return indicators.isEmpty()
    }



    protected void applyPlugins(Project project)
    {
        project.apply plugin: 'maven'
        project.apply plugin: 'maven-publish'

        if (AntBuild.isApplicable(project))
        {
            if (shouldDoBuild(project))
                project.apply plugin: 'org.labkey.antBuild'
        }
        else
        {
            if (ModuleResources.isApplicable(project))
                project.apply plugin: 'org.labkey.moduleResources'

            if (SpringConfig.isApplicable(project))
                project.apply plugin: 'org.labkey.springConfig'

            if (Webapp.isApplicable(project))
                project.apply plugin: 'org.labkey.webapp'

            if (ClientLibraries.isApplicable(project))
                project.apply plugin: 'org.labkey.clientLibraries'

            if (NpmRun.isApplicable(project))
                project.apply plugin: 'org.labkey.npmRun'
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
                                if(PropertiesUtils.LABKEY_VERSION_PATTERN.matcher(line))
                                {
                                    File globalProperties = project.rootProject.file("gradle.properties")

                                    // Search gradle properties for labkey version
                                    globalProperties.find {
                                        String prop ->
                                            Matcher labkeyVersionMatcher = Pattern.compile("labkeyVersion=").matcher(prop)
                                            if (labkeyVersionMatcher.find())
                                            {
                                                newLine = newLine.replace(matcher.group(), prop.replace(labkeyVersionMatcher.group(), ""))
                                                return true
                                            }
                                            return false
                                    }
                                }
                                else
                                {
                                    newLine = newLine.replace(matcher.group(), (String) project.lkModule.getPropertyValue(matcher.group(1), ""))
                                }
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
                    jar.archiveVersion.set(BuildUtils.getModuleFileVersion(project))
                    jar.archiveExtension.set('module')
                    jar.destinationDirectory = project.buildDir
            }

            Task moduleFile = project.tasks.module
            if (ModuleResources.isApplicable(project))
                moduleFile.dependsOn(project.tasks.processModuleResources)
            if (SpringConfig.isApplicable(project))
                moduleFile.dependsOn(project.tasks.processResources)
            moduleFile.dependsOn(moduleXmlTask)
            setJarManifestAttributes(project, (Manifest) moduleFile.manifest)
            if (project.getPlugins().findPlugin(ClientLibraries.class) != null && !LabKeyExtension.isDevMode(project))
                moduleFile.dependsOn(project.tasks.compressClientLibs)
            project.tasks.build.dependsOn(moduleFile)
            project.tasks.clean.dependsOn(project.tasks.cleanModule)

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
                            copy.into project.staging.modulesDir
                        }
                        project.copy { CopySpec copy ->
                            copy.from moduleFile
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
                        delete.doFirst {
                            undeployModule(project)
                            undeployJspJar(project)
                            Api.deleteModulesApiJar(project)
                        }
                    })
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

        if (hasClientLibraries(project))
        {
            project.tasks.register("zipWebDir", Zip) {
                Zip task ->
                    task.group = GroupNames.MODULE
                    task.description = "Create a zip file from the exploded module web directory"
                    task.archiveBaseName.set(project.name)
                    task.archiveClassifier.set(LabKey.CLIENT_LIBS_CLASSIFER)
                    from project.labkey.explodedModuleWebDir
                    task.destinationDirectory = project.file("${project.buildDir}/${project.libsDirName}")
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

    static boolean hasClientLibraries(Project project)
    {
        return ClientLibraries.isApplicable(project) || Gwt.isApplicable(project) || Webapp.isApplicable(project)
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
        String moduleFilePrefix = "${project.tasks.module.baseName}-"
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
                        return file.isDirectory() && file.getName().startsWith(moduleFilePrefix)
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
                project.tasks.register("pomFile", PomFile)  {
                    PomFile pFile ->
                        pFile.group = GroupNames.PUBLISHING
                        pFile.description = "create the pom file for this project's api jar"
                        pFile.pomProperties = project.lkModule.modProperties
                }
//                project.tasks.register("modulePomFile", ModulePomFile)  {
//                    ModulePomFile pFile ->
//                        pFile.group = GroupNames.PUBLISHING
//                        pFile.description = "create the pom file for this project's .module file"
//                        pFile.pomProperties = project.lkModule.modProperties
//                }
                project.publishing {
                    publications {
                        libs(MavenPublication) { pub ->
                            if (project.hasProperty('module'))
                                pub.artifact(project.tasks.module)
                            if (project.hasProperty('apiJar'))
                                pub.artifact(project.tasks.apiJar)
                            else if (project.path.equals(BuildUtils.getApiProjectPath(project.gradle))
                                    || project.path.equals(BuildUtils.getInternalProjectPath(project.gradle)))
                            {
                                pub.artifact(project.tasks.jar)
                            }
                        }

//                        if (project.hasProperty('module'))
//                        {
//                            module(MavenPublication) { pub ->
//                                // can't produce more than one main artifact for a given project, so we can put the .module
//                                // artifact in a different group, but we need to make a corresponding pom file.  For now,
//                                // we leave the .module in the same group as the api jar.
////                                pub.groupId = "org.labkey.module"
//                                pub.artifact(project.tasks.module)
//                            }
//                        }
//                        if (project.hasProperty('apiJar'))
//                        {
//                            apiLib(MavenPublication) { pub ->
//                                pub.artifact(project.tasks.apiJar)
//                            }
//                        }
//                        else if (project.path.equals(BuildUtils.getApiProjectPath(project.gradle)))
//                        {
//                            apiLib(MavenPublication) { pub ->
//                                pub.artifact(project.tasks.jar)
//                            }
//                        }
                    }

                    if (BuildUtils.shouldPublish(project))
                    {
                        project.artifactoryPublish {
                            if (project.hasProperty('module'))
                            {
//                                dependsOn project.tasks.modulePomFile
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
                            dependsOn project.tasks.pomFile
                            publications('libs')
//                            publications('module', 'apiLib')
                        }
                    }

                }
            }
        }
    }

    private void addDependencies(Project project)
    {
        Project serverProject = project.findProject(":server")
        if (serverProject != null)
        {
            BuildUtils.addLabKeyDependency(project: serverProject, config: 'modules', depProjectPath: project.path, depProjectConfig: 'published', depExtension: 'module')
            // This is done after the project is evaluated otherwise the dependencies for the modules configuration will not have been added yet.
            project.afterEvaluate({
                if (project.configurations.findByName("modules") != null)
                    project.configurations.modules.dependencies.each {
                        Dependency dep ->
                            if (dep instanceof ProjectDependency && dep.dependencyProject.getProjectDir().exists())
                            {
                                ProjectDependency projectDep = (ProjectDependency) dep
                                BuildUtils.addLabKeyDependency(project: serverProject, config: 'modules', depProjectPath: projectDep.dependencyProject.getPath(), depProjectConfig: 'published', depExtension: 'module')
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
