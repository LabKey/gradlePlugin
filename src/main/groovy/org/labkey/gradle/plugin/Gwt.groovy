/*
 * Copyright (c) 2016-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileTree
import org.gradle.api.specs.AndSpec
import org.gradle.api.tasks.JavaExec
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

    private static final String GWT_EXTENSION = ".gwt.xml"

    static boolean isApplicable(Project project)
    {
        // HACK!  We should move the gwtsrc from internal to api, where it is actually referenced
        return project.name != "internal" && project.file(SOURCE_DIR).exists()
    }

    @Override
    void apply(Project project)
    {
        project.apply plugin: 'java-base'
        project.extensions.create("gwt", GwtExtension)
        if (LabKeyExtension.isDevMode(project))
        {
            project.gwt.style = "PRETTY"
            project.gwt.draftCompile = true
            project.gwt.allBrowserCompile = false
        }

        addConfigurations(project)
        addDependencies(project)
        addSourceSet(project)
        addTasks(project)
    }

    private void addConfigurations(Project project)
    {

        project.configurations
                {
                    gwtCompile
                }
    }

    private void addDependencies(Project project)
    {
        String gxtGroup = (BuildUtils.compareVersions(project.gxtVersion, "2.2.5") > 0) ? "com.sencha.gxt" : "com.extjs"

        project.dependencies {
            gwtCompile "com.google.gwt:gwt-user:${project.gwtVersion}",
                    "com.google.gwt:gwt-dev:${project.gwtVersion}",
                    "${gxtGroup}:gxt:${project.gxtVersion}",
                    "com.allen-sauer.gwt.dnd:gwt-dnd:${project.gwtDndVersion}",
                    "javax.validation:validation-api:${project.validationApiVersion}"
        }

    }

    private void addSourceSet(Project project)
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

    private void addTasks(Project project)
    {
        Map<String, String> gwtModuleClasses = getGwtModuleClasses(project)
        List<Task> gwtTasks = new ArrayList<>(gwtModuleClasses.size());
        gwtModuleClasses.entrySet().each {
             gwtModuleClass ->

                Task compileTask = project.task(
                        'compileGwt' + gwtModuleClass.getKey(),
                        group: GroupNames.GWT,
                        type: JavaExec,
                        description: "compile GWT source files for " + gwtModuleClass.getKey()  + " into JS",
                        { JavaExec java ->
                            GString extrasDir = "${project.buildDir}/${project.gwt.extrasDir}"
                            String outputDir = project.labkey.explodedModuleWebDir

                            java.inputs.files(project.sourceSets.gwt.java.srcDirs)

                            java.outputs.dir extrasDir
                            java.outputs.dir outputDir

                            // Workaround for incremental build (GRADLE-1483)
                            java.outputs.upToDateSpec = new AndSpec()

                            java.doFirst {
                                project.file(extrasDir).mkdirs()
                                project.file(outputDir).mkdirs()
                            }

                            if (!LabKeyExtension.isDevMode(project))
                            {
                                java.doLast new GzipAction()
                            }

                            java.main = 'com.google.gwt.dev.Compiler'

                            /* The Ant user override property
                                <!-- Use environment variable gwt-user.jar, if set -->
                                <condition property="gwt-user-override" value="${env.gwt-user-override}">
                                    <isset property="env.gwt-user-override"/>
                                </condition>
                                <condition property="gwt-user-override" value="${env.LABKEY_GWT_USER_OVERRIDE}">
                                    <isset property="env.LABKEY_GWT_USER_OVERRIDE"/>
                                </condition>
                             */
                            def paths = []
                            if (!project.gwt.allBrowserCompile)
                            {
                                String gwtBrowser = System.getenv('LABKEY_GWT_USER_OVERRIDE')
                                if (StringUtils.isEmpty(gwtBrowser))
                                    gwtBrowser = System.getenv('gwt-user-override')
                                if (StringUtils.isEmpty(gwtBrowser))
                                    gwtBrowser = "gwt-user-firefox"
                                paths += ["${project.rootProject.rootDir}/external/lib/build/${gwtBrowser}"]
                            }
                            paths += [
                                    project.sourceSets.gwt.compileClasspath,       // Dep
                                    project.sourceSets.gwt.java.srcDirs,           // Java source
                                    project.project(":server:internal").file(project.gwt.srcDir),
                            ]
                            java.classpath paths

                            java.args =
                                    [
                                            '-war', outputDir,
                                            '-style', project.gwt.style,
                                            '-logLevel', project.gwt.logLevel,
                                            '-extra', extrasDir,
                                            '-deploy', extrasDir,
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
                )
                gwtTasks.add(compileTask)
        }
        def compileGwt = project.task("compileGwt",
                dependsOn: gwtTasks,
                description: 'compile all GWT source files into JS',
                group: GroupNames.GWT
        )
        project.tasks.classes.dependsOn(compileGwt)
    }

    private static Map<String, String> getGwtModuleClasses(Project project)
    {
        File gwtSrc = project.file(project.gwt.srcDir)
        FileTree tree = project.fileTree(dir: gwtSrc, includes: ["**/*${GWT_EXTENSION}"]);
        Map<String, String> nameToClass = new HashMap<>();
        String separator = System.getProperty("file.separator").equals("\\") ? "\\\\" : System.getProperty("file.separator");
        for (File file : tree.getFiles())
        {
            String className = file.getPath()
            className = className.substring(gwtSrc.getPath().length() + 1); // lop off the part of the path before the package structure
            className = className.replaceAll(separator, "."); // convert from path to class package
            className = className.substring(0, className.indexOf(GWT_EXTENSION)); // remove suffix
            nameToClass.put(file.getName().substring(0, file.getName().indexOf(GWT_EXTENSION)),className);
        }
        return nameToClass;
    }

}

