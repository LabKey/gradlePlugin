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
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.PropertiesUtils

/**
 * Task for stopping a tomcat instance
 */
class StopTomcat extends DefaultTask
{
    @TaskAction
    void action()
    {
        if (BuildUtils.useEmbeddedTomcat(project))
            stopEmbeddedTomcat()
        else
            stopLocalTomcat()
    }

    void stopLocalTomcat()
    {
        project.tomcat.validateCatalinaHome()
        project.javaexec( {
            JavaExecSpec java ->
                java.mainClass = "org.apache.catalina.startup.Bootstrap"
                java.classpath  { ["${project.tomcat.catalinaHome}/bin/bootstrap.jar", "${project.tomcat.catalinaHome}/bin/tomcat-juli.jar"] }
                java.systemProperties["user.dir"] = project.tomcat.catalinaHome
                java.args = ["stop"]
                java.ignoreExitValue = true
        })
    }

    void stopEmbeddedTomcat()
    {
        def applicationProperties = PropertiesUtils.getApplicationProperties(project)
        def port = applicationProperties.getProperty("management.server.port", applicationProperties.getProperty("server.port"))
        def endpoint =  "${project.hasProperty("useSsl") ? "https" : "http"}://localhost:$port/actuator/shutdown"
        def command = "curl -X POST $endpoint"
        this.logger.info("Sending command to $endpoint")
        def proc = command.execute()
        proc.waitFor()
        if (proc.exitValue() != 0)
            this.logger.warn("Shutdown command exited with non-zero status ${proc.exitValue()}.")
        else
            this.logger.quiet("Shutdown successful")
    }
}
