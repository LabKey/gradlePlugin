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


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.labkey.gradle.task.CopyJsp

import org.labkey.gradle.task.JspCompile2Java
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames

/**
 * Used to generate the jsp jar file for a module.
 */
class Jsp implements Plugin<Project>
{
    public static final String BASE_NAME_EXTENSION = "_jsp"

    static boolean isApplicable(Project project)
    {
        return !getJspFileTree(project).isEmpty()
    }

    private static FileTree getJspFileTree(Project project)
    {
        FileTree jspTree = project.fileTree("src").matching
                {
                    include('**/*.jsp')
                }
        jspTree += project.fileTree("resources").matching
                {
                    include("**/*.jsp")
                }
        return jspTree
    }

    @Override
    void apply(Project project)
    {
        project.apply plugin: 'java-base'

        addSourceSet(project)
        addConfiguration(project)
        addDependencies(project)
        addJspTasks(project)
    }

    private void addSourceSet(Project project)
    {
        project.sourceSets
                {
                    jsp {
                        java {
                            srcDirs = [BuildUtils.getBuildDirFile(project, JspCompile2Java.CLASSES_DIR).getPath()]
                        }
                    }
                }
    }

    private void addConfiguration(Project project)
    {
        project.configurations
                {
                    jspTagLibs
                }
        project.configurations.named('jspImplementation') {
            resolutionStrategy {
                force "javax.servlet:javax.servlet-api:${project.servletApiVersion}"
            }
        }
    }

    private void addDependencies(Project project)
    {
        project.dependencies
                {
                    if (!project.hasProperty("ignoreApiDep")) {
                        BuildUtils.addLabKeyDependency(project: project, config: "jspImplementation", depProjectPath: BuildUtils.getApiProjectPath(project.gradle), depVersion: project.labkeyVersion)
                        BuildUtils.addLabKeyDependency(project: project, config: "jspImplementation", depProjectPath: BuildUtils.getRemoteApiProjectPath(project.gradle), depVersion: BuildUtils.getLabKeyClientApiVersion(project))
                        BuildUtils.addLabKeyDependency(project: project, config: "jspTagLibs", depProjectPath: BuildUtils.getApiProjectPath(project.gradle), depVersion: project.labkeyVersion, depExtension: 'module', transitive: false)
                    }
                    jspImplementation project.files(project.tasks.jar)
                    if (project.hasProperty('apiJar'))
                        jspImplementation project.files(project.tasks.apiJar)
                    BuildUtils.addTomcatBuildDependencies(project, "jspImplementation")
                }
    }

    private static void addJspTasks(Project project)
    {
        project.tasks.register('listJsps') {
            Task task ->
                task.group = GroupNames.JSP
                task.doLast {
                    getJspFileTree(project).each ({
                        println it.absolutePath
                    })
                }
        }


        project.tasks.register('copyJsp', CopyJsp) {
            CopyJsp task ->
                task.group = GroupNames.JSP
                task.description = "Copy jsp files to jsp compile directory"
        }

        def copyTagLibs = getCopyTagLibs(project)
        if (BuildUtils.isIntellij()) {
            // We need this declaration for IntelliJ to be able to find the .tld files, but if we include
            // it for the command line, there will be lots of warnings about .tld files on the classpath where
            // they don't belong ("CLASSPATH element .../labkey.tld is not a JAR.").  These warnings may appear if
            // building within IntelliJ but perhaps we can live with that (for now).
            project.dependencies.add("implementation", copyTagLibs.get().inputs.files)
        }

        project.tasks.register('jsp2Java', JspCompile2Java) {
           JspCompile2Java task ->
               task.webappDirectory = BuildUtils.getBuildDirFile(project, CopyJsp.WEBAPP_DIR)
               task.group = GroupNames.JSP
               task.description = "compile jsp files into Java classes"

               if (project.hasProperty('apacheTomcatVersion'))
                   task.inputs.property 'apacheTomcatVersion', project.property('apacheTomcatVersion')
               else
                   task.outputs.cacheIf { false } // Just don't cache if we can't be sure of the Jasper version
               task.inputs.files project.tasks.copyJsp
               task.inputs.files project.tasks.copyTagLibs
               task.compileClasspath.from(project.configurations.jspCompileClasspath)
               if (project.hasProperty('apiJar'))
                   task.dependsOn('apiJar')
               task.dependsOn('jar')
        }

        project.tasks.named('jsp2Java') {
            notCompatibleWithConfigurationCache("ant.jasper doesn't seem completely compatible")
        }

        project.tasks.named('compileJspJava').configure {
            Task task ->
                task.dependsOn project.tasks.jsp2Java
                task.outputs.cacheIf({true} )
        }

        project.tasks.register('jspJar', Jar) {
             Jar jar ->
                 jar.group = GroupNames.JSP
                 jar.description = "produce jar file of jsps"
                 jar.from project.sourceSets.jsp.output
                 jar.archiveBaseName.set("${project.name}${BASE_NAME_EXTENSION}")
                 jar.dependsOn(project.tasks.compileJspJava)
                 jar.outputs.cacheIf({true})
         }

        project.tasks.assemble.dependsOn(project.tasks.jspJar)
    }

    private static TaskProvider getCopyTagLibs(Project project)
    {
        return project.tasks.register("copyTagLibs", Copy) {
            Copy task ->
                task.group = GroupNames.JSP
                task.description = "Copy the web.xml, tag library (.tld), and JSP Fragment (.jspf) files to jsp compile directory"
                task.configure({ CopySpec copy ->
                    String prefix = ""
                    if (project.findProject(BuildUtils.getApiProjectPath(project.gradle)) != null) {
                        // Copy taglib from source
                        copy.from project.rootProject.file("${BuildUtils.convertPathToRelativeDir(BuildUtils.getApiProjectPath(project.gradle))}/webapp")
                    }
                    else {
                        // Copy taglib from API module dependency
                        copy.from( { project.zipTree(project.configurations.jspTagLibs.getSingleFile()) } )
                        copy.filesMatching("web/WEB-INF/*") {it.path = it.path.replace("web/", "/") }
                        prefix = "web/"
                        // 'path.replace' leaves some empty directories
                        copy.setIncludeEmptyDirs false
                    }
                    copy.into project.layout.buildDirectory.dir(CopyJsp.WEBAPP_DIR)
                    copy.include "${prefix}WEB-INF/web.xml"
                    copy.include "${prefix}WEB-INF/*.tld"
                    copy.include "${prefix}WEB-INF/*.jspf"
                })
        }
    }
}
