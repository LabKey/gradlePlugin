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
package org.labkey.gradle.plugin

import com.sun.jdi.*
import com.sun.jdi.connect.AttachingConnector
import com.sun.jdi.connect.Connector
import com.sun.jdi.connect.IllegalConnectorArgumentsException
import org.apache.commons.lang3.SystemUtils
import org.gradle.api.*
import org.gradle.api.file.DeleteSpec
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.labkey.gradle.plugin.extension.TeamCityExtension
import org.labkey.gradle.task.PickDb
import org.labkey.gradle.task.RunTestSuite
import org.labkey.gradle.task.TeamCityDbSetup
import org.labkey.gradle.task.WriteStartupProperties
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.DatabaseProperties
import org.labkey.gradle.util.GroupNames

import java.time.Duration

/**
 * Creates tasks for TeamCity to run its tests suites based on properties set in a build configuration (particularly for
 * the database properties)
 */
class TeamCity extends Tomcat
{
    private static final String TEAMCITY_INFO_FILE = "teamcity-info.xml"
    private static final String TEST_CONFIGS_DIR = "configs/config-test" // TODO remove once NLP is not conifgured on TC
    private static final String NLP_CONFIG_FILE = "nlpConfig.xml" // TODO remove once NLP is not configured on TC
    private static final String PIPELINE_CONFIG_FILE =  "pipelineConfig.xml"
    private static final Duration TOMCAT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(15)

    private TeamCityExtension extension

    @Override
    void apply(Project project)
    {
        extension = project.extensions.findByType(TeamCityExtension.class)
        if (extension == null)
            extension = project.extensions.create("teamCity", TeamCityExtension, project)
        // we apply the parent plugin after creating the teamCity extension because we need some of the properties
        // from TeamCity's configuration when creating the UITestExtension on TeamCity
        super.apply(project)
        project.tomcat.assertionFlag = "-ea"
        String truststoreFile = "${System.getProperty("user.home")}/localhost.truststore"
        if (project.file(truststoreFile).exists())
        {
            project.tomcat.trustStore = "-Djavax.net.ssl.trustStore=${truststoreFile}"
            project.tomcat.trustStorePassword = "-Djavax.net.ssl.trustStorePassword=changeit"
        }
        project.tomcat.disableRecompileJsp = true
        project.tomcat.ignoreModuleSource = !(Boolean) extension.getTeamCityProperty("allowResourceReloading", false)
        project.tomcat.debugPort = extension.getTeamCityProperty("tomcat.debug") // Tomcat intermittently hangs on shutdown if we don't specify a debug port
        project.tomcat.catalinaOpts = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${project.tomcat.debugPort} -Dproject.root=${project.rootProject.projectDir.absolutePath}"

        addTasks(project)
    }

    private void addTasks(Project project)
    {
        project.tasks.register("setTeamCityAgentPassword", JavaExec) {
            JavaExec task ->
                task.group = GroupNames.TEST_SERVER
                task.description = "Set the password for use in running tests"
                task.dependsOn(project.tasks.jar)
                task.mainClass.set("org.labkey.test.util.PasswordUtil")
                task.classpath(project.configurations.uiTestRuntimeClasspath, project.tasks.jar)
                task.systemProperty("labkey.server", TeamCityExtension.getLabKeyServer(project))
                task.args("set", TeamCityExtension.getLabKeyUsername(project), TeamCityExtension.getLabKeyPassword(project))
        }

        project.tasks.register("cleanTestLogs", Delete) {
            Delete task ->
                task.group = GroupNames.TEST_SERVER
                task.description = "Removes log files from Tomcat and TeamCity"
                task.dependsOn project.tasks.cleanLogs
                task.configure { DeleteSpec delete ->
                    delete.delete "${project.projectDir}/${TEAMCITY_INFO_FILE}"
                }
        }

        // Captured here because the task actions below are stored in the configuration cache, so they must not
        // reference this plugin, which holds the extension that references the project
        String debugPort = extension.getTeamCityProperty("tomcat.debug")

        project.tasks.named("stopLabKey").configure {
            it.doLast { Task task ->
                ensureShutdown(task.logger, debugPort)
            }
        }

        project.tasks.named("stopTomcat").configure {
            it.doLast { Task task ->
                ensureShutdown(task.logger, debugPort)
            }
        }

        project.tasks.register("killChrome") {
            Task task ->
                task.group = GroupNames.TEST_SERVER
                task.description = "Kill Chrome processes"
                task.doLast {
                    killChrome(it.ant)
                }
        }

        project.tasks.register("killFirefox") {
            Task task ->
                task.group = GroupNames.TEST_SERVER
                task.description = "Kill Firefox processes"
                task.doLast {
                    killFirefox(it.ant)
                }
        }

        project.tasks.register("createStartupPropertyFile", WriteStartupProperties) {
            WriteStartupProperties task ->
                task.propertiesFile.set(TeamCityExtension.startupPropertiesFile(project, '99_teamcity_startup.properties'))
                task.propertiesContent.set(extension.getTeamCityProperty('labkey.startup.properties'))
        }

        project.tasks.named("startLabKey").configure {
            dependsOn(project.tasks.createStartupPropertyFile)
        }

        project.tasks.named("startTomcat").configure {
            dependsOn(project.tasks.createStartupPropertyFile)
        }

        project.tasks.register("validateConfiguration") {
            Task task ->
                task.doFirst
                        {
                            if (!extension.isValidForTestRun())
                                throw new GradleException("TeamCity configuration problem(s): ${extension.validationMessages.join('; ')}")

                            task.logger.info("teamcity.build.branch.is_default: ${extension.getTeamCityProperty('teamcity.build.branch.is_default')}")
                            task.logger.info("teamcity.build.branch: ${extension.getTeamCityProperty('teamcity.build.branch')}")
                        }
        }

        List<TaskProvider> ciTests = new ArrayList<>()
        for (DatabaseProperties properties : project.teamCity.databaseTypes)
        {
            String shortType = properties.shortType
            if (shortType == null || shortType.isEmpty())
                continue
            Provider<Task> pickDbTask
            String pickDbTaskName = "pick${shortType.capitalize()}"
            try {
                pickDbTask = project.tasks.named(pickDbTaskName)
            } catch (UnknownTaskException ignore) {
                project.tasks.register(pickDbTaskName, PickDb) {
                    PickDb task ->
                        task.group = GroupNames.TEST_SERVER
                        task.description = "Copy properties file for running tests for ${shortType}"
                        task.dbType = "${shortType}"
                        task.dbPropertiesChanged = true
                        task.driverFiles.setFrom(project.configurations.driver)
                }
                pickDbTask = project.tasks.named(pickDbTaskName)
            }

            String suffix = properties.dbTypeAndVersion.capitalize()
            String setUpTaskName = "setUp${suffix}"
            project.tasks.register(setUpTaskName, TeamCityDbSetup) {
                TeamCityDbSetup task ->
                    task.group = GroupNames.TEST_SERVER
                    task.description = "Get database properties set up for running tests for ${suffix}"
                    task.setDatabaseProperties(properties)
                    task.dropDatabase = extension.dropDatabase
                    task.dbPropertiesChanged = true
                    task.driverFiles.setFrom(project.configurations.driver)
                    task.testValidationOnly = Boolean.parseBoolean( extension.getTeamCityProperty("testValidationOnly"))
                    task.dependsOn (pickDbTask)
            }

            TaskProvider setUpDbTask = project.tasks.named(setUpTaskName)
            project.project(BuildUtils.getTestProjectPath(project.gradle)).tasks.startLabKey.mustRunAfter(setUpDbTask)
            project.project(BuildUtils.getTestProjectPath(project.gradle)).tasks.startTomcat.mustRunAfter(setUpDbTask)
            String ciTestTaskName = "ciTests" + properties.dbTypeAndVersion.capitalize()
            project.tasks.register(ciTestTaskName, RunTestSuite) {
                RunTestSuite task ->
                    task.group = GroupNames.TEST_SERVER
                    task.description = "Run a test suite for ${properties.dbTypeAndVersion} on the TeamCity server"
                    task.dependsOn(setUpDbTask)
                    task.dbProperties = properties
                    task.mustRunAfter(project.tasks.validateConfiguration)
                    task.mustRunAfter(project.tasks.cleanTestLogs)
                    task.mustRunAfter(project.tasks.startLabKey)
                    task.mustRunAfter(project.tasks.startTomcat)
            }

            ciTests.add(project.tasks.named(ciTestTaskName))

        }

        if (!extension.getTeamCityProperty('labkey.startup.includeDistModules').isBlank())
        {
            String inheritedDistPath = extension.getTeamCityProperty('labkey.startup.includeDistModules')
            project.evaluationDependsOn(inheritedDistPath)
            def includeDistModulesTask = project.tasks.register("includeDistModules", WriteStartupProperties) {
                WriteStartupProperties task ->
                    task.group = GroupNames.TEST_SERVER
                    task.description = "Generate server properties file to run with modules from a specified distribution"
                    project.logger.info("inheriting from distribution ${inheritedDistPath}")
                    Set<String> includeModules = new HashSet<>()
                    project.project(inheritedDistPath).configurations.distribution.dependencies.each {
                        includeModules.add(it.getName())
                    }

                    includeModules.addAll(extension.getTeamCityProperty('labkey.startup.includeDistModules.additional').split(','))

                    task.propertiesFile.set(TeamCityExtension.startupPropertiesFile(project, '00_modulesInclude.properties'))
                    task.propertiesContent.set('ModuleLoader.include;startup=' + String.join(',', includeModules))
            }

            project.tasks.named("startLabKey").configure {
                it.dependsOn(includeDistModulesTask)
            }
            project.tasks.named("startTomcat").configure {
                it.dependsOn(includeDistModulesTask)
            }
        }

        project.tasks.register("ciTests") {
            Task task ->
                task.group = GroupNames.TEST_SERVER
                task.dependsOn( ciTests )
                task.dependsOn( project.tasks.validateConfiguration, project.tasks.startLabKey, project.tasks.cleanTestLogs)
                task.description = "Run a test suite on the TeamCity server"
                task.doLast(
             {
                        killFirefox(task.ant)
                    }
                )
        }
        project.tasks.named("startLabKey").configure {
            it.mustRunAfter(project.tasks.cleanTestLogs)
        }
        project.tasks.named("startTomcat").configure {
            it.mustRunAfter(project.tasks.cleanTestLogs)
        }
    }

    private static void killChrome(AntBuilder ant)
    {
        if (SystemUtils.IS_OS_WINDOWS)
        {
            ant.exec(executable: "taskkill")
                    {
                        arg(line:"/F /IM chromedriver.exe" )
                    }
            ant.exec(executable: "taskkill")
                    {
                        arg(line:"/F /IM chrome.exe" )
                    }
        }
        else if (SystemUtils.IS_OS_UNIX)
        {
            ant.exec(executable: "killall")
                    {
                        arg(line:  "-q -KILL chromedriver")
                    }
            ant.exec(executable: "killall")
                    {
                        arg(line: "-q -KILL chrome")
                    }
            ant.exec(executable: "killall")
                    {
                        arg(line: "-q KILL BrowserBlocking")
                    }
        }
    }

    private static void killFirefox(AntBuilder ant)
    {
        if (SystemUtils.IS_OS_WINDOWS)
        {
            ant.exec(executable: "taskkill")
                    {
                        arg(line: "/F /IM firefox.exe")
                    }
            ant.exec(executable: "taskkill")
                    {
                        arg(line: "/F /IM geckodriver.exe")
                    }
        }
        else if (SystemUtils.IS_OS_UNIX)
        {
            ant.exec(executable: "killall")
                    {
                        arg(line: "-q firefox")
                    }
            ant.exec(executable: "killall")
                    {
                        arg(line: "-q firefox-bin")
                    }
            ant.exec(executable: "killall")
                    {
                        arg(line: "-q geckodriver")
                    }
        }
    }

    private static void threadDumpAndKill(AttachingConnector connector, int port) throws IllegalConnectorArgumentsException, IOException, IncompatibleThreadStateException
    {
        Map<String, Connector.Argument> arguments = connector.defaultArguments()
        arguments.get("hostname").setValue("localhost")
        arguments.get("port").setValue(Integer.toString(port))
        long startTime = System.currentTimeMillis()
        VirtualMachine vm

        try
        {
            vm = connector.attach(arguments)
        }
        catch (IOException ignore)
        {
            println("Unable to connect to VM at localhost:" + port + ", VM may already be shut down")
            return
        }

        println("Waiting for graceful Tomcat shutdown.")

        try
        {
            while (System.currentTimeMillis() - startTime < TOMCAT_SHUTDOWN_TIMEOUT.toMillis())
            {
                vm.mirrorOf("") // Poke VM to see if alive. Will throw 'VMDisconnectedException' if not
                sleep(500)
            }

            println("Tomcat did not shutdown. Forcing exit via debug port: " + port)
            vm.suspend()
            for (ThreadReference threadReference : vm.allThreads()) {
                dumpThread(threadReference)
                println()
            }
            vm.resume()
            vm.exit(1)
            println("Killed remote VM")
        }
        catch (VMDisconnectedException ignore)
        {
            println("VM at localhost:" + port + " exited normally")
        }
    }

    private static void dumpThread(ThreadReference threadReference) throws IncompatibleThreadStateException
    {
        println("Thread '" + threadReference.name() + "', status = " + getStatus(threadReference))
        ObjectReference objectRef = threadReference.currentContendedMonitor()
        if (objectRef != null)
        {
            StringBuilder line = new StringBuilder()
            line.append("\t\tAttempting to acquire monitor for ")
            line.append(objectRef.referenceType().name())
            line.append("@")
            line.append(objectRef.uniqueID())
            if (objectRef.owningThread() != null)
            {
                line.append(" held by thread '")
                line.append(objectRef.owningThread().name())
                line.append("'")
            }
            println(line)
        }
        for (ObjectReference ownedMonitor : threadReference.ownedMonitors())
        {
            println("\t\tHolding monitor for " + ownedMonitor.referenceType().name() + "@" + ownedMonitor.uniqueID())
        }
        for (StackFrame stackFrame : threadReference.frames())
        {
            StringBuilder line = new StringBuilder()
            line.append("\t")
            line.append(stackFrame.location().declaringType().name())
            line.append(".").append(stackFrame.location().method().name())
            line.append("(")
            try
            {
                line.append(stackFrame.location().sourceName())
            }
            catch (AbsentInformationException ignore)
            {
                line.append("UnknownSource")
            }
            line.append(":").append(stackFrame.location().lineNumber())
            line.append(")")
            println(line.toString())
        }
    }

    private static String getStatus(ThreadReference threadReference)
    {
        switch (threadReference.status())
        {
            case ThreadReference.THREAD_STATUS_MONITOR:
                return "WAITING FOR MONITOR"
            case ThreadReference.THREAD_STATUS_NOT_STARTED:
                return "NOT STARTED"
            case ThreadReference.THREAD_STATUS_RUNNING:
                return "RUNNING"
            case ThreadReference.THREAD_STATUS_SLEEPING:
                return "SLEEPING"
            case ThreadReference.THREAD_STATUS_WAIT:
                return "WAITING"
            case ThreadReference.THREAD_STATUS_ZOMBIE:
                return "ZOMBIE"
            default:
                return "UNKNOWN"
        }
    }

    private static void ensureShutdown(Logger logger, String debugPort)
    {
        if (!debugPort.isEmpty())
        {
            logger.debug("Ensuring shutdown using port ${debugPort}")
            try
            {
                AttachingConnector socketConnector = null
                for (AttachingConnector connector : Bootstrap.virtualMachineManager().attachingConnectors())
                {
                    logger.debug("Found connector ${connector.name()} with class ${connector.getClass().getName()}")
                    if ("com.sun.jdi.SocketAttach".equals(connector.name()))
                    {
                        socketConnector = connector
                    }
                }
                if (socketConnector == null)
                    throw new GradleException("No SocketAttach connector found!")
                else
                {
                    int port = Integer.parseInt(debugPort)
                    threadDumpAndKill(socketConnector, port)
                }
            }
            catch (NumberFormatException e)
            {
                throw new GradleException("Invalid port number: ${debugPort}", e)
            }
        }
    }
}

