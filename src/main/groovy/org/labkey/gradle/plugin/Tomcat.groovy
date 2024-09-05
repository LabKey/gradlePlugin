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


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DeleteSpec
import org.gradle.api.tasks.Delete
import org.labkey.gradle.plugin.extension.ServerDeployExtension
import org.labkey.gradle.plugin.extension.TomcatExtension
import org.labkey.gradle.plugin.extension.UiTestExtension
import org.labkey.gradle.task.StartTomcat
import org.labkey.gradle.task.StopTomcat
import org.labkey.gradle.util.GroupNames

/**
 * Plugin for starting and stopping tomcat
 */
class Tomcat implements Plugin<Project>
{
    public static final String EMBEDDED_LOG_FILE_NAME = "logs/embeddedTomcat.log"

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
            tomcat.assertionFlag = Boolean.valueOf((String) testEx.getTestConfig("disableAssertions")) ? "-da" : "-ea"
        }
        tomcat.catalinaOpts = "-Dproject.root=${project.rootProject.projectDir.absolutePath}"
        addTasks(project)
    }

    private static void addTasks(Project project)
    {
        project.tasks.register("startTomcat", StartTomcat) {
            StartTomcat task ->
                task.group = GroupNames.WEB_APPLICATION
                task.description = "Start the embedded Tomcat instance"
        }

        project.tasks.register(
                "stopTomcat", StopTomcat) {
            StopTomcat task ->
                task.group = GroupNames.WEB_APPLICATION
                task.description = "Stop the embedded Tomcat instance"
        }

        project.tasks.register("cleanLogs", Delete) {
            Delete task ->
                var logDir = "${ServerDeployExtension.getEmbeddedServerDeployDirectory(project)}/logs"
                task.group = GroupNames.WEB_APPLICATION
                task.description = "Delete logs from ${logDir}"
                task.configure { DeleteSpec spec -> spec.delete project.fileTree(logDir) }
        }
    }
}

