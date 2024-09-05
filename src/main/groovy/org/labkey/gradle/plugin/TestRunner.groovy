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

import org.gradle.api.Project
import org.gradle.api.Task
import org.labkey.gradle.plugin.extension.TeamCityExtension
import org.labkey.gradle.task.RunTestSuite
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames

class TestRunner extends UiTest
{

    protected void addTasks(Project project)
    {
        super.addTasks(project)

        addPasswordTasks(project)

        addDataFileTasks(project)

        addTestSuiteTask(project)

        addAspectJ(project)

    }

    @Override
    protected void addSourceSets(Project project)
    {
        project.sourceSets {
            uiTest {
                java {
                    srcDirs = [project.file("src")]
                    // we add the test/src directories from all projects because the test suites encompass tests
                    // across modules.
                    project.rootProject.allprojects { Project otherProj ->
                        if (otherProj.file(TEST_SRC_DIR).exists())
                        {
                            srcDirs += otherProj.file(TEST_SRC_DIR)
                        }
                        else if (otherProj.file("test/${TEST_SRC_DIR}").exists())  // special case for labmodules and scharp directories
                        {
                            srcDirs += otherProj.file("test/${TEST_SRC_DIR}")
                        }
                    }
                }
                resources {
                    srcDirs = [project.file("resources")]
                    project.rootProject.allprojects { Project otherProj ->
                        if (otherProj.file(TEST_RESOURCES_DIR).exists())
                        {
                            srcDirs += otherProj.file(TEST_RESOURCES_DIR)
                        }
                    }
                }
            }
        }

    }


    private void addPasswordTasks(Project project)
    {

        project.tasks.register("setPassword") {
            Task task ->
                task.group = GroupNames.TEST
                task.description = "Set the password for use in running tests"
                task.dependsOn(project.tasks.jar)
                task.doFirst({
                    project.javaexec({
                        mainClass = "org.labkey.test.util.PasswordUtil"
                        classpath {
                            [project.configurations.uiTestRuntimeClasspath, project.tasks.jar]
                        }
                        systemProperties["labkey.server"] = TeamCityExtension.getLabKeyServer(project)
                        args = ["set"]
                        standardInput = System.in
                    })
                })
        }


        project.tasks.register("ensurePassword") {
            Task task ->
                task.group = GroupNames.TEST
                task.description = "Ensure that the password property used for running tests has been set"
                task.dependsOn(project.tasks.jar)
                task.doFirst({
                    project.javaexec({
                        mainClass = "org.labkey.test.util.PasswordUtil"
                        classpath {
                            [project.configurations.uiTestRuntimeClasspath, project.tasks.jar]
                        }
                        systemProperties["labkey.server"] = TeamCityExtension.getLabKeyServer(project)
                        args = ["ensure"]
                        standardInput = System.in
                    })
                })
        }
    }

    private static void addDataFileTasks(Project project)
    {
        List<File> directories = new ArrayList<>()

        project.rootProject.allprojects({ Project p ->
            File dataDir = p.file("test/sampledata")
            if (dataDir.exists())
            {
                directories.add(dataDir)
            }
        })

        File sampleDataFile = BuildUtils.getBuildDirFile(project,"sampledata.dirs")

        project.tasks.register("writeSampleDataFile") {
            Task task ->
                task.group = GroupNames.TEST
                task.description = "Produce the file with all sampledata directories for use in running tests"
                task.inputs.files directories
                task.outputs.file sampleDataFile
                task..doLast({
                    List<String> dirNames = new ArrayList<>()

                    directories.each({File file ->
                        dirNames.add(file.getAbsolutePath())
                    })

                    FileOutputStream outputStream = new FileOutputStream(sampleDataFile)
                    Writer writer = null
                    try
                    {
                        writer = new OutputStreamWriter(outputStream)
                        dirNames.add("${project.rootDir}/sampledata")
                        dirNames.add("${project.rootDir}/${BuildUtils.convertPathToRelativeDir(BuildUtils.getTestProjectPath(project.gradle))}/data")
                        writer.write(String.join(";", dirNames))
                    }
                    finally
                    {
                        if (writer != null)
                            writer.close()
                    }
                })
        }
    }

    private void addTestSuiteTask(Project project)
    {
        project.logger.debug("TestRunner: addTestSuiteTask for ${project.path}")
        // Using project.tasks.register here cause an error:
        // Cannot add task 'uiTests' as a task with that name already exists
        project.tasks.register("uiTests", RunTestSuite) {
            RunTestSuite task ->
                task.group = GroupNames.VERIFICATION
                task.description = "Run a LabKey test suite as defined by ${project.file(testRunnerExt.propertiesFileName)} and overridden on the command line by -P<prop>=<value> "
        }
    }

    private void addAspectJ(Project project)
    {
        project.tasks.named('compileUiTestJava').configure {doLast {
            ant.taskdef(
                resource: "org/aspectj/tools/ant/taskdefs/aspectjTaskdefs.properties",
                classpath: project.configurations.aspectj.asPath
            )
            ant.iajc(
                destdir: BuildUtils.getBuildDirFile(project,"classes/java/uiTest/").getPath(),
                source: project.sourceCompatibility,
                target: project.targetCompatibility,
                classpath: project.configurations.uiTestRuntimeClasspath.asPath,
                {
                    project.sourceSets.uiTest.java.srcDirs.each {
                        src(path: it)
                    }
                }
            )
        }}
    }
}
