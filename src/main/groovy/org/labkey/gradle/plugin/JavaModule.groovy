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

import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar
import org.labkey.gradle.plugin.extension.ModuleExtension
import org.labkey.gradle.task.CheckForVersionConflicts
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames

/**
 * This class is used for building a LabKey Java module (one that typically resides in a *modules
 * directory).  It defines tasks for building the jar files (<module>_jsp.jar, <module>.jar)
 * as well as tasks for copying resources to the build directory.
 *
 */
class JavaModule extends FileModule
{
    public static final DIR_NAME = "src";

    static boolean isApplicable(Project project)
    {
        return project.file(DIR_NAME).exists()
    }

    @Override
    protected void applyPlugins(Project project)
    {
        project.apply plugin: 'maven-publish'

        if (AntBuild.isApplicable(project))
        {
            if (shouldDoBuild(project))
                project.apply plugin: 'org.labkey.antBuild'
        }
        else
        {
            // this comes before the setJavaBuildProperties because we declare
            // dependencies to tasks from this plugin in the jar configuration
            if (XmlBeans.isApplicable(project))
                project.apply plugin: 'org.labkey.xmlBeans'

            setJavaBuildProperties(project)

            if (Api.isApplicable(project))
                project.apply plugin: 'org.labkey.api'

            if (SpringConfig.isApplicable(project))
                project.apply plugin: 'org.labkey.springConfig'

            if (Webapp.isApplicable(project))
                project.apply plugin: 'org.labkey.webapp'

            ClientLibraries.addTasks(project)

            if (Jsp.isApplicable(project))
                project.apply plugin: 'org.labkey.jsp'

            if (Gwt.isApplicable(project))
                project.apply plugin: 'org.labkey.gwt'

            if (NpmRun.isApplicable(project))
                project.apply plugin: 'org.labkey.npmRun'


            if (UiTest.isApplicable(project))
            {
                project.apply plugin: 'org.labkey.uiTest'
            }
        }
    }

    @Override
    protected void addConfigurations(Project project)
    {
        super.addConfigurations(project)
        project.configurations
                {
                    labkey // use this configuration for dependencies to labkey API jars that are needed for a module
                           // but don't need to show up in the dependencies.txt and jars.txt
                    // TODO I think what's really wanted here is external.extendsFrom(api) and external.extendsFrom(implementation)
                    // Then we change the gradle files to use api and implementation as per usual.  Perhaps we can then do away with
                    // external altogether if we also get rid of jars.txt and we'll just copy from the api and implementation configurations
                    // into explodedModule/lib.
                    api.extendsFrom(external)
                    implementation.extendsFrom(external)
                    external.extendsFrom(runtimeOnly)
                    implementation.extendsFrom(labkey)
                    dedupe {
                        canBeConsumed = false
                        canBeResolved = true
                    }
                }
        project.configurations.labkey.setDescription("Dependencies on LabKey API jars that are needed for a module when the full module is not required.  These don't need to be declared in jars.txt.")
        project.configurations.dedupe.setDescription("Dependencies that come from the base modules and can therefore be excluded from other modules")
    }

    protected void setJavaBuildProperties(Project project)
    {
        project.libsDirName = 'explodedModule/lib'

        addSourceSets(project)

        project.jar { Jar jar ->
            jar.archiveBaseName.set(project.name)
            if (XmlBeans.isApplicable(project))
            {
                project.tasks.compileJava.dependsOn(project.tasks.schemasCompile)
                jar.from(project.tasks.compileJava, project.tasks.schemasCompile)
            }
            jar.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
            jar.exclude '**/*.java'
        }
    }

    private void addSourceSets(Project project)
    {
        ModuleResources.addSourceSet(project)
        project.sourceSets {
            main {
                java {
                    srcDirs = XmlBeans.isApplicable(project) ? ['src', "$project.buildDir/$XmlBeans.CLASS_DIR"] : ['src']
                }
            }
        }
    }

    static boolean isDatabaseSupported(Project project, String database)
    {
        ModuleExtension extension = project.extensions.getByType(ModuleExtension.class)
        String supported = extension.getPropertyValue("SupportedDatabases")
        return supported == null || supported.contains(database)
    }

    @Override
    protected void addTasks(Project project)
    {
        super.addTasks(project)
        setJarManifestAttributes(project, project.jar.manifest)

        // We do this afterEvaluate to allow all dependencies to be declared before checking
        project.afterEvaluate({
            FileCollection externalFiles = getTrimmedExternalFiles(project)
            FileCollection allJars = externalFiles

            project.tasks.register("copyExternalLibs", Copy) {
                Copy task ->
                    task.group = GroupNames.MODULE
                    task.description = "copy the dependencies declared in the 'external' configuration into the lib directory of the built module"
                    task.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
                    task.configure
                            { CopySpec copy ->
                                copy.from externalFiles
                                copy.into "${project.labkey.explodedModuleDir}/lib"
                                copy.include "*.jar"
                            }
            }

            if (project.tasks.findByName("module") != null)
            {
                project.tasks.module.dependsOn(project.tasks.copyExternalLibs)
                if (project.file("src").exists())
                {
                    project.tasks.module.dependsOn(project.tasks.jar)
                    allJars = allJars + project.tasks.jar.outputs.files
                }
                if (project.hasProperty('apiJar'))
                {
                    project.tasks.module.dependsOn(project.tasks.apiJar)
                    allJars = allJars + project.tasks.apiJar.outputs.files
                }
                if (project.hasProperty('jspJar'))
                {
                    project.tasks.module.dependsOn(project.tasks.jspJar)
                    allJars = allJars + project.tasks.jspJar.outputs.files
                }
                if (project.hasProperty('schemasJar'))
                {
                    project.tasks.module.dependsOn(project.tasks.schemasJar)
                    allJars = allJars + project.tasks.schemasJar.outputs.files
                }
            }
            project.tasks.register(
                    "checkModuleJarVersions", CheckForVersionConflicts)
                    { CheckForVersionConflicts task ->

                        task.group = GroupNames.MODULE
                        task.description = "Check for conflicts in version numbers of jar files to be included in the module and files already in the build directory ${project.labkey.explodedModuleDir}/lib." +
                                "Default action on detecting a conflict is to fail.  Use -PversionConflictAction=[delete|fail|warn] to change this behavior.  The value 'delete' will cause the " +
                                "conflicting version(s) in the ${project.labkey.explodedModuleDir}/lib directory to be removed."
                        task.directory = new File("${project.labkey.explodedModuleDir}/lib")
                        task.extension = "jar"
                        task.cleanTask = "${project.path}:clean"
                        task.collection = allJars
                    }

            project.tasks.copyExternalLibs.dependsOn(project.tasks.checkModuleJarVersions)
            Project serverProject = BuildUtils.getServerProject(project)
            if (serverProject != null)
                serverProject.tasks.checkVersionConflicts.dependsOn(project.tasks.checkModuleJarVersions)
        })
    }

    /**
     * Returns the set of files included in the external configuration for a project
     * with the jar files already included in the base modules removed.  If the project
     * is a base module, only the jars included in the api module are removed.  If the
     * project is the api module, all jars in its external configuration are included.
     * @param project the project whose external dependencies are to be removed
     * @return the collection of files that contain the dependencies
     */
    static FileCollection getTrimmedExternalFiles(Project project)
    {
        FileCollection labkeyConfig = project.configurations.labkey
        FileCollection config = project.configurations.external

        if (config == null && labkeyConfig == null)
            return null

        config = labkeyConfig == null ? config : (config == null ? labkeyConfig : config + labkeyConfig)


        // trim nothing from api
        if (BuildUtils.isApi(project))
            return config
        else // all other modules should remove everything in the dedupe configuration
        {
            return config - project.configurations.dedupe
        }
    }
}

