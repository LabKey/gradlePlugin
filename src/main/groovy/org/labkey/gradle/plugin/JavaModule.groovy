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

import org.gradle.api.Plugin
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
import org.labkey.gradle.util.TaskUtils

/**
 * This class is used for building a LabKey Java module (one that typically resides in the server/modules
 * directory).  It defines tasks for building the jar files (<module>_jsp.jar, <module>.jar)
 * as well as tasks for copying resources to the build directory.
 *
 */
class JavaModule implements Plugin<Project>
{
    public static final DIR_NAME = "src";

    static boolean isApplicable(Project project)
    {
        return project.file(DIR_NAME).exists()
    }

    @Override
    void apply(Project project)
    {
        if (FileModule.shouldDoBuild(project, false)) {
            applyPlugins(project)
            addConfigurations(project)
            addTasks(project)
        }
    }

    protected void applyPlugins(Project project)
    {
        project.apply plugin: 'maven-publish'
        project.apply plugin: 'org.labkey.build.fileModule'

        if (!AntBuild.isApplicable(project))
        {
            // this comes before the setJavaBuildProperties because we declare
            // dependencies to tasks from this plugin in the jar configuration
            if (XmlBeans.isApplicable(project))
                project.apply plugin: 'org.labkey.build.xmlBeans'

            setJavaBuildProperties(project)

            if (Api.isApplicable(project))
                project.apply plugin: 'org.labkey.build.api'

            if (Jsp.isApplicable(project))
                project.apply plugin: 'org.labkey.build.jsp'

            if (Gwt.isApplicable(project))
                project.apply plugin: 'org.labkey.build.gwt'

            if (UiTest.isApplicable(project))
            {
                project.apply plugin: 'org.labkey.build.uiTest'
            }
        }
    }

    protected void addConfigurations(Project project)
    {
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
            jar.outputs.cacheIf({true})
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

    protected static void addTasks(Project project)
    {
        def populateLib = project.tasks.register("populateExplodedLib", Copy) {
            CopySpec copy ->
                copy.group = GroupNames.MODULE
                copy.description = "Copy the jar files needed for the module into the ${project.labkey.explodedModuleLibDir} directory from their respective output directories"
                copy.into project.labkey.explodedModuleLibDir
                if (project.tasks.findByName("jar") != null)
                    copy.from project.tasks.named("jar")
                if (project.tasks.findByName("apiJar") != null)
                    copy.from project.tasks.named("apiJar")
                if (project.tasks.findByName("jspJar") != null)
                    copy.from project.tasks.named('jspJar')
                if (project.tasks.findByName("copyExternalLibs") != null)
                    copy.from project.tasks.named('copyExternalLibs')
        }
        populateLib.configure {
            it.doFirst {
                File explodedLibDir = new File(project.labkey.explodedModuleLibDir)
                if (explodedLibDir.exists())
                    explodedLibDir.delete()
            }
        }
        project.tasks.named('module').configure {dependsOn(populateLib)}
        // We do this afterEvaluate to allow all dependencies to be declared before checking
        project.afterEvaluate({
            FileCollection externalFiles = getTrimmedExternalFiles(project)

            project.tasks.register("copyExternalLibs", Copy) {
                Copy task ->
                    File destination = new File(project.buildDir, "libsExternal");
                    task.group = GroupNames.MODULE
                    task.description = "copy the dependencies declared in the 'external' configuration into the lib directory of the built module"
                    task.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
                    task.configure
                            { CopySpec copy ->
                                copy.from externalFiles
                                copy.into destination
                                copy.include "*.jar"
                            }
                    task.doFirst({
                        // first clean out the directory, if it existed from a previous build
                        if (destinationDir.exists()) // I think Windows doesn't deal well with delete without this check first
                            destination.deleteDir();
                    })
            }

            TaskUtils.configureTaskIfPresent(project, 'module', {
                dependsOn(project.tasks.copyExternalLibs)
                if (project.tasks.findByName("jar") != null)
                {
                    dependsOn(project.tasks.jar)
                }
                if (project.tasks.findByName('apiJar') != null)
                {
                    dependsOn(project.tasks.apiJar)
                }
                if (project.tasks.findByName('jspJar') != null)
                {
                    dependsOn(project.tasks.jspJar)
                }
            } )

            project.tasks.register(
                    "checkModuleJarVersions", CheckForVersionConflicts)
                    { CheckForVersionConflicts task ->

                        FileCollection allJars = externalFiles
                        if (project.tasks.findByName("jar") != null)
                        {
                            allJars = allJars + project.tasks.jar.outputs.files
                        }
                        if (project.tasks.findByName('apiJar') != null)
                        {
                            allJars = allJars + project.tasks.apiJar.outputs.files
                        }
                        if (project.tasks.findByName('jspJar') != null)
                        {
                            allJars = allJars + project.tasks.jspJar.outputs.files
                        }

                        task.group = GroupNames.MODULE
                        task.description = "Check for conflicts in version numbers of jar files to be included in the module and files already in the build directory ${project.labkey.explodedModuleLibDir}." +
                                "Default action on detecting a conflict is to fail.  Use -PversionConflictAction=[delete|fail|warn] to change this behavior.  The value 'delete' will cause the " +
                                "conflicting version(s) in the ${project.labkey.explodedModuleLibDir} directory to be removed."
                        task.directory = new File("${project.labkey.explodedModuleLibDir}")
                        task.extension = "jar"
                        task.cleanTask = "${project.path}:clean"
                        task.collection = allJars
                    }

            project.tasks.named("copyExternalLibs").configure {dependsOn(project.tasks.named("checkModuleJarVersions"))}
            Project serverProject = BuildUtils.getServerProject(project)
            if (serverProject != null)
                serverProject.tasks.named("checkVersionConflicts").configure {dependsOn(project.tasks.named("checkModuleJarVersions"))}
            // This is necessary to avoid warnings with gradle 7 because, I believe, of the resource and java source sets
            // overlapping in certain cases.  By declaring this dependency, we enable some optimizations from Gradle because
            // it can know in what order to run tasks that put files into a common directory,
            // but it might be nice to remove overlaps somewhere so this isn't necessary.
            project.tasks.named("compileJava").configure {dependsOn(project.tasks.named("processModuleResources"))}
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

