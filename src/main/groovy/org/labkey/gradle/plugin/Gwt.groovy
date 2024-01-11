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
import org.gradle.api.file.FileTree
import org.gradle.api.specs.AndSpec
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.labkey.gradle.plugin.extension.GwtExtension
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.task.GzipAction
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames

/**
 * Used to compile GWT source files into Javascript
 */
class Gwt implements Plugin<Project>
{
    public static final String SOURCE_DIR = "gwtsrc"

    private Project project
    private static final String GWT_EXTENSION = ".gwt.xml"

    static boolean isApplicable(Project project)
    {
        return project.file(SOURCE_DIR).exists()
    }

    @Override
    void apply(Project project)
    {
        this.project = project
        project.apply plugin: 'java-base'
        project.extensions.create("gwt", GwtExtension)
        if (LabKeyExtension.isDevMode(project))
        {
            project.gwt.style = "PRETTY"
            project.gwt.draftCompile = true
            project.gwt.allBrowserCompile = false
        }

        addSourceSets()
        addDependencies()
        addTasks()
    }

    private void addDependencies()
    {
        project.dependencies {
            gwtImplementation "org.gwtproject:gwt-user:${project.gwtVersion}",
                    "org.gwtproject:gwt-dev:${project.gwtVersion}"
        }
        if (project.hasProperty("validationJakartaApiVersion"))
            project.dependencies {
                gwtImplementation "jakarta.validation:jakarta.validation-api:${project.validationJakartaApiVersion}"
            }
        else if (project.hasProperty("validationApiVersion"))
            project.dependencies {
                gwtImplementation "javax.validation:validation-api:${project.validationApiVersion}"
            }
    }

    private void addSourceSets()
    {
        project.sourceSets {
            gwt {
                java {
                    srcDir project.gwt.srcDir
                }
            }
            main {
                java {
                    srcDir project.gwt.srcDir
                }
            }
        }
    }

    private void addTasks()
    {
        Map<String, String> gwtModuleClasses = getGwtModuleClasses(project)
        List<TaskProvider> gwtTasks = new ArrayList<>(gwtModuleClasses.size())
        gwtModuleClasses.entrySet().each {
             gwtModuleClass ->

                String taskName ='compileGwt' + gwtModuleClass.getKey()
                project.tasks.register(taskName, JavaExec) {
                    JavaExec java ->
                        java.outputs.cacheIf {true}
                        java.group = GroupNames.GWT
                        java.description = "compile GWT source files for " + gwtModuleClass.getKey() + " into JS"

                        File extrasDir = BuildUtils.getBuildDirFile(project, project.gwt.extrasDir)
                        File outputDir = BuildUtils.getBuildDirFile(project, project.gwt.outputDir)

                        java.inputs.files(project.sourceSets.gwt.java.srcDirs)
                        String extrasDirPath = extrasDir.getPath()
                        String outputDirPath = outputDir.getPath()

                        java.outputs.dir extrasDir
                        java.outputs.dir outputDir

                        // Workaround for incremental build (GRADLE-1483)
                        java.outputs.upToDateSpec = new AndSpec()

                        java.doFirst {
                            extrasDir.mkdirs()
                            outputDir.mkdirs()
                        }

                        if (!LabKeyExtension.isDevMode(project))
                        {
                            java.doLast new GzipAction()
                        }

                        java.setMainClass('com.google.gwt.dev.Compiler')

                        def paths = []

                        paths += [
                                project.sourceSets.gwt.compileClasspath,       // Dep
                                project.sourceSets.gwt.java.srcDirs           // Java source
                        ]
                        String apiProjectPath = BuildUtils.getApiProjectPath(project.gradle)
                        if (project.findProject(apiProjectPath) != null && project.project(apiProjectPath).file(project.gwt.srcDir).exists())
                            paths += [project.project(apiProjectPath).file(project.gwt.srcDir)]
                        java.classpath paths

                        java.args =
                                [
                                        '-war', outputDirPath,
                                        '-style', project.gwt.style,
                                        '-logLevel', project.gwt.logLevel,
                                        '-extra', extrasDirPath,
                                        '-deploy', extrasDirPath,
                                        '-localWorkers', 4,
                                        gwtModuleClass.getValue()
                                ]
                        if (project.gwt.draftCompile)
                            java.args.add('-draftCompile')
                        java.jvmArgs =
                                [
                                        '-Xss1024k',
                                        '-Djava.awt.headless=true'
                                ]

                        java.maxHeapSize = '512m'

                }
                gwtTasks.add(project.tasks.named(taskName))
        }
        project.tasks.register('compileGwt', Copy) {
            Copy copy ->
                copy.from gwtTasks
                copy.into project.labkey.explodedModuleWebDir
                copy.description = "compile all GWT source files into JS and copy them to the module's web directory"
                copy.group = GroupNames.GWT
        }

        project.tasks.named("classes").configure {dependsOn(project.tasks.compileGwt)}
    }

    private static Map<String, String> getGwtModuleClasses(Project project)
    {
        File gwtSrc = project.file(project.gwt.srcDir)
        FileTree tree = project.fileTree(dir: gwtSrc, includes: ["**/*${GWT_EXTENSION}"])
        Map<String, String> nameToClass = new HashMap<>()
        String separator = System.getProperty("file.separator").equals("\\") ? "\\\\" : System.getProperty("file.separator")
        for (File file : tree.getFiles())
        {
            String className = file.getPath()
            className = className.substring(gwtSrc.getPath().length() + 1) // lop off the part of the path before the package structure
            className = className.replaceAll(separator, ".") // convert from path to class package
            className = className.substring(0, className.indexOf(GWT_EXTENSION)) // remove suffix
            nameToClass.put(file.getName().substring(0, file.getName().indexOf(GWT_EXTENSION)),className)
        }
        return nameToClass
    }

}

