/*
 * Copyright (c) 2016-2026 LabKey Corporation
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
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.labkey.gradle.plugin.Tomcat
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.ServerDeployExtension
import org.labkey.gradle.plugin.extension.TeamCityExtension
import org.labkey.gradle.util.BuildUtils

import java.util.stream.Collectors

@UntrackedTask(because="Output is a running process")
abstract class StartLabKey extends TeamCityPropertiesTask
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

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    final abstract DirectoryProperty deployDir = project.objects.directoryProperty().convention(ServerDeployExtension.getEmbeddedServerDeployDirectory(project))

    @OutputFile
    final abstract RegularFileProperty logFileProp = project.objects.fileProperty().convention(ServerDeployExtension.getEmbeddedServerDeployDirectory(project).file(Tomcat.EMBEDDED_LOG_FILE_NAME))

    @Input
    final abstract ListProperty<String> startupOpts = project.objects.listProperty(String).convention(getStartupOpts(project))

    @Input
    final abstract ListProperty<String> embeddedReflectionOpts = project.objects.listProperty(String).convention(getReflectionOptions(project))

    @TaskAction
    void action()
    {
        File jarFile = BuildUtils.getExecutableServerJar(deployDir.get().asFile)
        if (jarFile == null)
        {
            throw new GradleException("No jar file found in ${deployDir.get().asFile}.")
        }
        else
        {
            String javaHome = tomcatJavaHome.get()
            if (StringUtils.isEmpty(javaHome))
                throw new GradleException("JAVA_HOME must be set in order to start your embedded tomcat server.")
            File javaBin = new File(javaHome, "bin")
            File javaExec = new File(javaBin, SystemUtils.IS_OS_WINDOWS ? "java.exe" : "java")
            if (!javaExec.exists())
                throw new GradleException("Invalid value for JAVA_HOME. Could not find java command in ${javaExec}")
            String[] commandParts = [javaExec.getAbsolutePath()]
            commandParts += embeddedReflectionOpts.get()
            commandParts += startupOpts.get()
            commandParts += ["-jar", jarFile.getName()]

            File logFile = logFileProp.get().asFile
            if (!logFile.getParentFile().exists())
                logFile.getParentFile().mkdirs()
            if (!logFile.exists())
                logFile.createNewFile()
            FileOutputStream outputStream = new FileOutputStream(logFile)
            def envMap = new HashMap<>(System.getenv())
            String deployDirPath = deployDir.get().asFile.getAbsolutePath()
            envMap.put('PATH', "${deployDirPath}/bin${File.pathSeparator}${System.getenv("PATH")}")
            def env = []
            for (String key : envMap.keySet()) {
                env += "${key}=${envMap.get(key)}"
            }
            this.logger.info("Starting LabKey with command ${commandParts} and env ${env} in directory ${deployDirPath}")
            Process process = commandParts.execute(env, deployDir.get().asFile)
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

        if (TeamCityExtension.getTeamCityProperty(project, "labkey.heapDumpOnOutOfMemoryError", TeamCityExtension.isOnTeamCity(project)))
        {
            optsList.add("-XX:+HeapDumpOnOutOfMemoryError");
        }

        String extraCatalinaOpts = TeamCityExtension.getTeamCityProperty(project, "extraCatalinaOpts", "")
        if (!extraCatalinaOpts.isEmpty())
            optsList.addAll(extraCatalinaOpts.split("\\s+"))

        return optsList.stream()
                .filter({String opt -> return !StringUtils.isEmpty(opt)})
                .collect(Collectors.toList())

    }

    private static List<String> getReflectionOptions(Project project)
    {
        if (project.hasProperty(EMBEDDED_REFLECTION_PARAM)) {
            return List.of(((String) project.property(EMBEDDED_REFLECTION_PARAM)).trim().split("\\s+"))
        }
        else {
            return DEFAULT_EMBEDDED_REFLECTION_OPTS
        }
    }
}
