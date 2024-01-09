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
import org.gradle.internal.impldep.it.unimi.dsi.fastutil.Hash
import org.labkey.gradle.plugin.Tomcat
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.ServerDeployExtension
import org.labkey.gradle.plugin.extension.TeamCityExtension
import org.labkey.gradle.util.BuildUtils

import java.util.stream.Collectors

class StartTomcat extends DefaultTask
{
    @TaskAction
    void action()
    {
        if (BuildUtils.useEmbeddedTomcat(project))
            startEmbeddedTomcat()
        else
            startLocalTomcat()
    }

    private startEmbeddedTomcat()
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
            File javaExec = new File(javaHome, "bin/java")
            if (!javaExec.exists())
                throw new GradleException("Invalid value for JAVA_HOME. Could not find java command in ${javaExec}")
            String[] commandParts = [javaExec.getAbsolutePath()]
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

    private void startLocalTomcat()
    {
        project.tomcat.validateCatalinaHome()

        // we need to create the logs directory if it doesn't exist because Tomcat won't start without it,
        // and, annoyingly, this is not seen as an error for this action.
        if (!project.file("${project.tomcat.catalinaHome}/logs").exists())
            project.mkdir("${project.tomcat.catalinaHome}/logs")
        if (SystemUtils.IS_OS_UNIX)
        {
            project.ant.chmod(dir: "${project.tomcat.catalinaHome}/bin", includes: "**/*.sh", perm: "ug+rx")
        }
        project.ant.exec(
                spawn: true,
                dir: SystemUtils.IS_OS_WINDOWS ? "${project.tomcat.catalinaHome}/bin" : project.tomcat.catalinaHome,
                executable: SystemUtils.IS_OS_WINDOWS ? "cmd" : "bin/catalina.sh"
        )
                {
                    env(
                            key: "PATH",
                            path: "${BuildUtils.getServerProject(project).serverDeploy.binDir}${File.pathSeparator}${System.getenv("PATH")}"
                    )

                    String catalinaOpts = getStartupOpts(project).join(" ").replaceAll("\\s+", " ")

                    this.logger.debug("setting CATALINA_OPTS to ${catalinaOpts}")
                    env(
                            key: "CATALINA_OPTS",
                            value: catalinaOpts
                    )
                    if (TeamCityExtension.isOnTeamCity(project))
                    {
                        env(
                                key: "R_LIBS_USER",
                                value: System.getenv("R_LIBS_USER") != null ? System.getenv("R_LIBS_USER") : project.rootProject.file("sampledata/rlabkey")
                        )

                        def javaHome = TeamCityExtension.getTeamCityProperty(project, "tomcatJavaHome", System.getenv("JAVA_HOME"))
                        env (
                                key: "JAVA_HOME",
                                value: javaHome
                        )
                        env (
                                key: "JRE_HOME",
                                value: javaHome
                        )
                    }

                    if (SystemUtils.IS_OS_WINDOWS)
                    {
                        env(
                                key: "CLOSE_WINDOW",
                                value: true
                        )
                        arg(line: "/c start ")
                        arg(value: "'Tomcat Server'")
                        arg(value: "/B")
                        arg(value: "${project.tomcat.catalinaHome}/bin/catalina.bat")
                    }
                    arg(value: "start")
                }
        println("Waiting 5 seconds for tomcat to start...")
        project.ant.sleep(seconds: 5)
        println("Tomcat started.")
    }

    static List<String> getStartupOpts(Project project)
    {
        List<String> optsList = new ArrayList<>()
        optsList.add(project.tomcat.assertionFlag)
        optsList.add("-Ddevmode=${LabKeyExtension.isDevMode(project)}")
        optsList.addAll(project.tomcat.catalinaOpts.split(" "))
        optsList.add("-Xmx${TeamCityExtension.getTeamCityProperty(project, "Xmx", project.tomcat.maxMemory)}")
        if (project.tomcat.disableRecompileJsp)
            optsList.add("-Dlabkey.disableRecompileJsp=true")
        if (project.tomcat.ignoreModuleSource)
            optsList.add("-Dlabkey.ignoreModuleSource=true")
        optsList.add(project.tomcat.trustStore)
        optsList.add(project.tomcat.trustStorePassword)

        if (TeamCityExtension.isOnTeamCity(project) && SystemUtils.IS_OS_UNIX)
        {
            optsList.add("-DsequencePipelineEnabled=${TeamCityExtension.getTeamCityProperty(project, "sequencePipelineEnabled", false)}")
        }

        if (project.hasProperty("extraCatalinaOpts"))
            optsList.addAll(((String) project.property("extraCatalinaOpts")).split(" "))

        return optsList.stream()
                .filter({String opt -> return !StringUtils.isEmpty(opt)})
                .collect(Collectors.toList())

    }
}
