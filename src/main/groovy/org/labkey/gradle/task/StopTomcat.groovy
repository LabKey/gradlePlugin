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
package org.labkey.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.process.JavaExecSpec

/**
 * Task for stopping a tomcat instance
 */
class StopTomcat extends DefaultTask
{
    @TaskAction
    void exec()
    {
        project.javaexec( {
            JavaExecSpec java ->
                java.main = "org.apache.catalina.startup.Bootstrap"
                java.classpath  { ["${project.tomcatDir}/bin/bootstrap.jar", "${project.tomcatDir}/bin/tomcat-juli.jar"] }
                java.systemProperties["user.dir"] = project.tomcatDir
                java.args = ["stop"]
                java.ignoreExitValue = true
        })
    }
}
