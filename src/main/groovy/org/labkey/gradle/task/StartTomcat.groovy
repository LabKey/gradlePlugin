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

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.Tomcat
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.ServerDeployExtension
import org.labkey.gradle.plugin.extension.TeamCityExtension
import org.labkey.gradle.util.BuildUtils

import java.util.stream.Collectors

class StartTomcat extends DefaultTask
{
    private static final String EMBEDDED_REFLECTION_PARAM = "embeddedReflectionArgs"
    private static final List<String> DEFAULT_EMBEDDED_REFLECTION_OPTS = [
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.nio=ALL-UNNAMED",   // Needed for Snowflake JDBC
            "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
            "--add-opens=java.base/java.text=ALL-UNNAMED"
    ]

    @TaskAction
    void action()
    {
        File jarFile = BuildUtils.getExecutableServerJar(project)
        if (jarFile == null)
        {
            throw new GradleException("No jar file found in ${ServerDeployExtension.getEmbeddedServerDeployDirectory(project)}.")
        }
        else
        {
            String javaHome = TeamCityExtension.getTeamCityProperty(project, "tomcatJavaHome", System.getenv("JAVA_HOME"))
            if (StringUtils.isEmpty(javaHome))
                throw new GradleException("JAVA_HOME must be set in order to start your embedded tomcat server.")
            File javaBin = new File(javaHome, "bin")
            File javaExec = new File(javaBin, SystemUtils.IS_OS_WINDOWS ? "java.exe" : "java")
            if (!javaExec.exists())
                throw new GradleException("Invalid value for JAVA_HOME. Could not find java command in ${javaExec}")
            String[] commandParts = [javaExec.getAbsolutePath()]
            commandParts += getEmbeddedReflectionOpts(project)
            commandParts += getStartupOpts(project)
            commandParts += ["-jar", jarFile.getName()]

            File logFile = new File(ServerDeployExtension.getEmbeddedServerDeployDirectory(project), Tomcat.EMBEDDED_LOG_FILE_NAME)
            if (!logFile.getParentFile().exists())
                logFile.getParentFile().mkdirs()
            if (!logFile.exists())
                logFile.createNewFile()
            FileOutputStream outputStream = new FileOutputStream(logFile)
            def envMap = new HashMap<>(System.getenv())
            envMap.put('PATH', "${ServerDeployExtension.getEmbeddedServerDeployDirectory(project)}/bin${File.pathSeparator}${System.getenv("PATH")}")
            def env = []
            for (String key : envMap.keySet()) {
                env += "${key}=${envMap.get(key)}"
            }
            this.logger.info("Starting embedded tomcat with command ${commandParts} and env ${env} in directory ${ServerDeployExtension.getEmbeddedServerDeployDirectory(project)}")
            Process process = commandParts.execute(env, new File(ServerDeployExtension.getEmbeddedServerDeployDirectory(project)))
            process.consumeProcessOutput(outputStream, outputStream)
        }
    }

    static List<String> getStartupOpts(Project project)
    {
        List<String> optsList = new ArrayList<>()
        optsList.add(project.tomcat.assertionFlag)
        optsList.add("-Ddevmode=${LabKeyExtension.isDevMode(project)}".toString())
        optsList.addAll(project.tomcat.catalinaOpts.split(" "))
        optsList.add("-Xmx${TeamCityExtension.getTeamCityProperty(project, "Xmx", project.tomcat.maxMemory)}".toString())
        if (project.tomcat.disableRecompileJsp)
            optsList.add("-Dlabkey.disableRecompileJsp=true")
        if (project.tomcat.ignoreModuleSource)
            optsList.add("-Dlabkey.ignoreModuleSource=true")
        optsList.add(project.tomcat.trustStore)
        optsList.add(project.tomcat.trustStorePassword)

        if (TeamCityExtension.isOnTeamCity(project) && SystemUtils.IS_OS_UNIX)
        {
            optsList.add("-DsequencePipelineEnabled=${TeamCityExtension.getTeamCityProperty(project, "sequencePipelineEnabled", false)}".toString())
        }

        if (project.hasProperty("extraCatalinaOpts"))
            optsList.addAll(((String) project.property("extraCatalinaOpts")).split("\\s+"))

        return optsList.stream()
                .filter({String opt -> return !StringUtils.isEmpty(opt)})
                .collect(Collectors.toList())

    }

    private static List<String> getEmbeddedReflectionOpts(Project project)
    {
        if (project.hasProperty(EMBEDDED_REFLECTION_PARAM)) {
            return ((String) project.property(EMBEDDED_REFLECTION_PARAM)).trim().split("\\s+")
        }
        else {
            return DEFAULT_EMBEDDED_REFLECTION_OPTS
        }
    }
}
