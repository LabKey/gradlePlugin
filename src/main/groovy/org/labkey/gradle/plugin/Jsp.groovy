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
import org.labkey.gradle.task.JspCompile2Java
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames

/**
 * Used to generate the jsp jar file for a module.
 */
class Jsp implements Plugin<Project>
{
    public static final String BASE_NAME_EXTENSION = "_jsp"

    public static final String WEBAPP_DIR = "jspWebappDir/webapp"

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

        addConfiguration(project)
        addDependencies(project)
        addSourceSet(project)
        addJspTasks(project)
    }

    private void addSourceSet(Project project)
    {
        project.sourceSets
                {
                    jsp {
                        java {
                            srcDirs = ["${project.buildDir}/${JspCompile2Java.CLASSES_DIR}"]
                        }
                    }
                }
    }

    private void addConfiguration(Project project)
    {
        project.configurations
                {
                    jspImplementation
                    jsp
                    jspTagLibs
                }
        project.configurations.getByName('jspImplementation') {
            resolutionStrategy {
                force "javax.servlet:servlet-api:${project.servletApiVersion}"
            }
        }
    }

    private void addDependencies(Project project)
    {
        project.dependencies
                {
                    BuildUtils.addLabKeyDependency(project: project, config: "jspImplementation", depProjectPath: BuildUtils.getApiProjectPath(project.gradle), depVersion: project.labkeyVersion)
                    BuildUtils.addLabKeyDependency(project: project, config: "jspImplementation", depProjectPath: BuildUtils.getInternalProjectPath(project.gradle), depVersion: project.labkeyVersion)
                    BuildUtils.addLabKeyDependency(project: project, config: "jspTagLibs", depProjectPath: BuildUtils.getApiProjectPath(project.gradle), depVersion: project.labkeyVersion, depExtension: 'module', transitive: false)
                    jspImplementation project.files(project.tasks.jar)
                    if (project.hasProperty('apiJar'))
                        jspImplementation project.files(project.tasks.apiJar)
                    BuildUtils.addTomcatBuildDependencies(project, "jspImplementation")

                    jsp ("org.apache.tomcat:tomcat-jasper:${project.apacheTomcatVersion}") { transitive = false }
                    jsp ("org.apache.tomcat:tomcat-juli:${project.apacheTomcatVersion}") { transitive = false }
                }
        // We need this declaration for IntelliJ to be able to find the .tld files, but if we include
        // it for the command line, there will be lots of warnings about .tld files on the classpath where
        // they don't belong ("CLASSPATH element .../labkey.tld is not a JAR.").  These warnings may appear if
        // building within IntelliJ but perhaps we can live with that (for now).
//        if (BuildUtils.isIntellij())
//            project.dependencies.add("implementation", getCopyTagLibsBase(project.gradle).get().inputs.files)
    }

    private void addJspTasks(Project project)
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

        project.tasks.register('copyJsp', Copy)
                {
                    Copy task ->
                        task.group = GroupNames.JSP
                        task.description = "Copy jsp files to jsp compile directory"
                        task.configure({ CopySpec copy ->
                            copy.from 'src'
                            copy.into "${project.buildDir}/${WEBAPP_DIR}"
                            copy.include '**/*.jsp'
                        })
                        task.doFirst {
                            project.delete "${project.buildDir}/${WEBAPP_DIR}/org"
                        }
                }


        project.tasks.register('copyResourceJsp', Copy)
                {
                    Copy task ->
                        task.group = GroupNames.JSP
                        task.description = "Copy resource jsp files to jsp compile directory"
                        task.configure({ CopySpec copy ->
                            copy.from 'resources'
                            copy.into "${project.buildDir}/${WEBAPP_DIR}/org/labkey/${project.name}"
                            copy.include '**/*.jsp'
                        })
                }

        def copyTagLibs = getCopyTagLibs(project)
        if (BuildUtils.isIntellij()) {
            project.dependencies.add("implementation", copyTagLibs.get().inputs.files)
        }

        project.tasks.register('jsp2Java', JspCompile2Java) {
           JspCompile2Java task ->
               task.webappDirectory = new File("${project.buildDir}/${WEBAPP_DIR}")
               task.group = GroupNames.JSP
               task.description = "compile jsp files into Java classes"

               task.inputs.files project.tasks.copyJsp
               task.inputs.files project.tasks.copyResourceJsp
               task.inputs.files project.tasks.copyTagLibs
               task.doFirst ({
                   project.delete task.getClassesDirectory()
               })
               if (project.hasProperty('apiJar'))
                   task.dependsOn('apiJar')
               task.dependsOn('jar')
        }

        project.tasks.compileJspJava {
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

        project.artifacts {
            jspImplementation project.tasks.jspJar
        }
        project.tasks.assemble.dependsOn(project.tasks.jspJar)
    }

    private TaskProvider getCopyTagLibs(Project project)
    {
        return project.tasks.register("copyTagLibs", Copy) {
            Copy task ->
                task.group = GroupNames.JSP
                task.description = "Copy the web.xml, tag library (.tld), and JSP Fragment (.jspf) files to jsp compile directory"
                task.configure({ CopySpec copy ->
                    String prefix = ""
                    if (project.findProject(BuildUtils.getApiProjectPath(project.gradle)) != null) {
                        // Copy taglib from source
                        copy.from "${BuildUtils.getApiProjectPath(project.gradle).replace(":", "/").substring(1)}/webapp"
                    }
                    else {
                        // Copy taglib from API module dependency
                        copy.from( { project.zipTree(project.configurations.jspTagLibs.getSingleFile()) } )
                        copy.filesMatching("web/WEB-INF/*") {it.path = it.path.replace("web/", "/") }
                        prefix = "web/"
                        // exclude intermediate directories to avoid empty directories in destination
                        copy.exclude "web"
                        copy.exclude "web/WEB-INF"
                    }
                    copy.into "${project.buildDir}/${WEBAPP_DIR}"
                    copy.include "${prefix}WEB-INF/web.xml"
                    copy.include "${prefix}WEB-INF/*.tld"
                    copy.include "${prefix}WEB-INF/*.jspf"
                })
        }
    }
}
