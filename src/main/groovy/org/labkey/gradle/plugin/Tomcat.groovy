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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DeleteSpec
import org.gradle.api.tasks.Delete
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.ServerDeployExtension
import org.labkey.gradle.plugin.extension.TomcatExtension
import org.labkey.gradle.plugin.extension.UiTestExtension
import org.labkey.gradle.task.StartTomcat
import org.labkey.gradle.task.StopTomcat
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames
import org.labkey.gradle.util.PropertiesUtils

/**
 * Plugin for starting and stopping tomcat
 */
class Tomcat implements Plugin<Project>
{
    private static final String EMBEDDED_LOG_FILE_NAME = "logs/embeddedTomcat.log"

    @Override
    void apply(Project project)
    {
        TomcatExtension tomcat = project.extensions.findByType(TomcatExtension.class)
        if (tomcat == null)
        {
            tomcat = project.extensions.create("tomcat", TomcatExtension, project)
        }
        if (project.plugins.hasPlugin(TestRunner.class))
        {
            UiTestExtension testEx = (UiTestExtension) project.getExtensions().getByType(UiTestExtension.class)
            tomcat.assertionFlag = Boolean.valueOf(testEx.getTestConfig("disableAssertions")) ? "-da" : "-ea"
        }
        tomcat.catalinaOpts = "-Dproject.root=${project.rootProject.projectDir.absolutePath}"

        addTasks(project, tomcat)
    }


    private static void addTasks(Project project, TomcatExtension tomcat)
    {
        project.tasks.register("startTomcat", StartTomcat) {
            StartTomcat task ->
                task.group = GroupNames.WEB_APPLICATION
                task.description = "Start the local Tomcat instance"
        }

        project.tasks.register(
                "stopTomcat", StopTomcat) {
            StopTomcat task ->
                task.group = GroupNames.WEB_APPLICATION
                task.description = "Stop the local Tomcat instance"
        }

        project.tasks.register("startEmbeddedTomcat", DefaultTask) {
            DefaultTask task ->
                task.group=GroupNames.WEB_APPLICATION
                task.description="Start the embedded Tomcat server"
                task.doFirst {
                    File jarFile = BuildUtils.getExecutableServerJar(project)
                    if (jarFile == null) {
                        throw new GradleException("No jar file found in ${ServerDeployExtension.getEmbeddedServerDeployDirectory(project)}.")
                    }
                    else {
                        String[] commandParts = ["java"]
                        if (LabKeyExtension.isDevMode(project))
                            commandParts += "-Ddevmode=true"
                        commandParts += ["-jar", jarFile.getName()]
                        File logFile = new File(ServerDeployExtension.getEmbeddedServerDeployDirectory(project), EMBEDDED_LOG_FILE_NAME)
                        if (!logFile.getParentFile().exists())
                            logFile.getParentFile().mkdirs()
                        new ProcessBuilder()
                                .directory(new File(ServerDeployExtension.getEmbeddedServerDeployDirectory(project)))
                                .command(commandParts)
                                .redirectOutput(logFile)
                                .redirectError(logFile)
                                .start()
                    }
                }
        }

        project.tasks.register("stopEmbeddedTomcat", DefaultTask) {
            DefaultTask task ->
                task.group=GroupNames.WEB_APPLICATION
                task.description = "Shut down embedded Tomcat server"
                task.doLast {

                    def applicationProperties = PropertiesUtils.getApplicationProperties(project)
                    def port = applicationProperties["server.port"]
                    def endpoint =  "${project.hasProperty("useSsl") ? "https" : "http"}://localhost:$port/actuator/shutdown"
                    def command = "curl -X POST $endpoint"
                    task.logger.quiet("Sending command to $endpoint")
                    def proc = command.execute()
                    proc.waitFor()
                    if (proc.exitValue() != 0)
                        task.logger.warn("Shutdown command exited with non-zero status ${proc.exitValue()}.")
                    else
                        task.logger.quiet("Shutdown successful")
                }
        }

        project.tasks.register("cleanLogs", Delete) {
            Delete task ->
                task.group = GroupNames.WEB_APPLICATION
                task.description = "Delete logs from ${tomcat.catalinaHome}"
                task.doFirst {tomcat.validateCatalinaHome()}
                task.configure(
                {
                    DeleteSpec spec -> spec.delete project.fileTree("${tomcat.catalinaHome}/logs")
                    }
                )
        }

        project.tasks.register("cleanTemp", DefaultTask) {
            DefaultTask task ->
                task.group = GroupNames.WEB_APPLICATION
                task.description = "Delete temp files from ${tomcat.catalinaHome}"
                task.doFirst {tomcat.validateCatalinaHome()}
                task.doLast(  {
                    // Note that we use the AntBuilder here because a fileTree in Gradle is a set of FILES only.
                    // Deleting a file tree will delete all the leaves of the directory structure, but none of the
                    // directories.
                    project.ant.delete(includeEmptyDirs: true, quiet: true)
                    {
                        fileset(dir: "${tomcat.catalinaHome}/temp")
                                {
                                    include(name: "**/*")
                                }
                    }
                    new File("${tomcat.catalinaHome}", "temp").mkdirs()
                })
        }

    }
}

