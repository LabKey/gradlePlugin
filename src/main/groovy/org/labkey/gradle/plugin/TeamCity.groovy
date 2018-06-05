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

import org.apache.commons.lang3.SystemUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.process.JavaExecSpec
import org.labkey.gradle.plugin.extension.ServerDeployExtension
import org.labkey.gradle.plugin.extension.TeamCityExtension
import org.labkey.gradle.task.DoThenSetup
import org.labkey.gradle.task.PickDb
import org.labkey.gradle.task.RunTestSuite
import org.labkey.gradle.task.UndeployModules
import org.labkey.gradle.util.DatabaseProperties
import org.labkey.gradle.util.GroupNames
import org.labkey.gradle.util.PropertiesUtils
import org.labkey.gradle.util.SqlUtils

import java.util.regex.Matcher
/**
 * Creates tasks for TeamCity to run its tests suites based on properties set in a build configuration (particularly for
 * the database properties)
 */
class TeamCity extends Tomcat
{
    private static final String TEAMCITY_INFO_FILE = "teamcity-info.xml"
    private static final String TEST_CONFIGS_DIR = "configs/config-test"
    private static final String NLP_CONFIG_FILE = "nlpConfig.xml"
    private static final String PIPELINE_CONFIG_FILE =  "pipelineConfig.xml"

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
        if (project.file("${project.tomcatDir}/localhost.truststore").exists())
        {
            project.tomcat.trustStore = "-Djavax.net.ssl.trustStore=${project.tomcatDir}/localhost.truststore"
            project.tomcat.trustStorePassword = "-Djavax.net.ssl.trustStorePassword=changeit"
        }
        project.tomcat.recompileJsp = false
        project.tomcat.debugPort = extension.getTeamCityProperty("tomcat.debug") // Tomcat intermittently hangs on shutdown if we don't specify a debug port
        project.tomcat.catalinaOpts = "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=${project.tomcat.debugPort} -Dproject.root=${project.rootProject.projectDir.absolutePath} -Xnoagent -Djava.compiler=NONE"

        addTasks(project)
    }

    private void addTasks(Project project)
    {
        project.task("setTeamCityAgentPassword",
                group: GroupNames.TEST_SERVER,
                description: "Set the password for use in running tests",
                {   Task task ->
                    task.dependsOn(project.tasks.testJar)
                    task.doLast {
                        project.javaexec({ JavaExecSpec spec ->
                            spec.main = "org.labkey.test.util.PasswordUtil"
                            spec.classpath {
                                [project.configurations.uiTestCompile, project.tasks.testJar]
                            }
                            spec.systemProperties["labkey.server"] = TeamCityExtension.getLabKeyServer(project)
                            spec.args = ["set", "teamcity@labkey.test", "yekbal1!"]
                        })
                    }
                }
        )

        project.task("cleanTestLogs",
                group: GroupNames.TEST_SERVER,
                description: "Removes log files from Tomcat and TeamCity",
                {
                    Task task ->
                    task.dependsOn project.tasks.cleanLogs, project.tasks.cleanTemp
                    task.doLast {
                        project.delete "${project.projectDir}/${TEAMCITY_INFO_FILE}"
                    }
                }
        )


        project.tasks.stopTomcat.dependsOn(project.tasks.debugClasses)
        project.tasks.stopTomcat.doLast (
                {
                    ensureShutdown(project)
                }
        )

        project.task("killChrome",
            group: GroupNames.TEST_SERVER,
            description: "Kill Chrome processes",
                {
                    Task task ->
                    task.doLast {
                        killChrome(project)
                    }
                }
        )

        project.task("killFirefox",
                group: GroupNames.TEST_SERVER,
                description: "Kill Firefox processes",
                {
                    Task task ->
                    task.doLast {
                        killFirefox(project)
                    }
                }
        )

        if (project.findProject(":externalModules:labModules:SequenceAnalysis") != null)
        {
            project.task("createPipelineConfig",
                    group: GroupNames.TEST_SERVER,
                    description: "Create pipeline configs for running tests on the test server",
                    type: Copy,
                    {
                        from project.project(":server").file(TEST_CONFIGS_DIR)
                        include PIPELINE_CONFIG_FILE
                        filter({ String line ->
                            Matcher matcher = PropertiesUtils.PROPERTY_PATTERN.matcher(line)
                            String newLine = line
                            while (matcher.find())
                            {
                                if (matcher.group(1).equals("SEQUENCEANALYSIS_CODELOCATION") || matcher.group(1).equals("SEQUENCEANALYSIS_TOOLS"))
                                    newLine = newLine.replace(matcher.group(), extension.getTeamCityProperty("additional.pipeline.tools"))
                                else if (matcher.group(1).equals("SEQUENCEANALYSIS_EXTERNALDIR"))
                                    newLine = newLine.replace(matcher.group(), project.project(":externalModules:labModules:SequenceAnalysis").file("pipeline_code/external").getAbsolutePath())
                            }
                            return newLine;

                        })
                        destinationDir = new File("${ServerDeployExtension.getServerDeployDirectory(project)}/config")
                    }
            )
        }

        project.task("createNlpConfig",
            group: GroupNames.TEST_SERVER,
            description: "Create NLP engine configs for the test server",
            type: Copy,
                {
                    from project.project(":server").file(TEST_CONFIGS_DIR)
                    include NLP_CONFIG_FILE
                    filter ({String line ->
                        Matcher matcher = PropertiesUtils.PROPERTY_PATTERN.matcher(line)
                        String newLine = line
                        while (matcher.find())
                        {
                            if (matcher.group(1).equals("enginePath"))
                                newLine = newLine.replace(matcher.group(), new File((String) project.labkey.externalDir, "nlp/nlp_engine.py").getAbsolutePath())
                        }
                        return newLine
                    }
                    )
                    destinationDir = new File("${ServerDeployExtension.getServerDeployDirectory(project)}/config")
                })

        if (project.findProject(":externalModules:labModules:SequenceAnalysis") != null)
        {
            project.tasks.startTomcat.dependsOn(project.tasks.createPipelineConfig)
        }
        project.tasks.startTomcat.dependsOn(project.tasks.createNlpConfig)

        project.task("validateConfiguration",
                {
                    doFirst
                            {
                                if (!extension.isValidForTestRun())
                                    throw new GradleException("TeamCity configuration problem(s): ${extension.validationMessages.join('; ')}")

                                project.logger.info("teamcity.build.branch.is_default: ${extension.getTeamCityProperty('teamcity.build.branch.is_default')}")
                                project.logger.info("teamcity.build.branch: ${extension.getTeamCityProperty('teamcity.build.branch')}")
                            }
                })


        List<Task> ciTests = new ArrayList<>()
        for (DatabaseProperties properties : project.teamCity.databaseTypes)
        {
            String shortType = properties.shortType
            if (shortType == null || shortType.isEmpty())
                continue
            String pickDbTaskName = "pick${shortType.capitalize()}"
            Task pickDbTask = project.tasks.findByName(pickDbTaskName)
            if (pickDbTask == null)
            {
                pickDbTask = project.task(pickDbTaskName,
                        group: GroupNames.TEST_SERVER,
                        description: "Copy properties file for running tests for ${shortType}",
                        type: PickDb,
                        { PickDb task ->
                            task.dbType = "${shortType}"
                            task.dbPropertiesChanged = true
                        }
                )
            }

            String suffix = properties.dbTypeAndVersion.capitalize()
            Task setUpDbTask = project.task("setUp${suffix}",
                group: GroupNames.TEST_SERVER,
                description: "Get database properties set up for running tests for ${suffix}",
                type: DoThenSetup,
                    {DoThenSetup task ->

                        task.setDatabaseProperties(properties)
                        task.dbPropertiesChanged = true
                        task.fn = {
                            properties.mergePropertiesFromFile()
                            if (extension.dropDatabase) {
                                if ((Boolean) extension.getTeamCityProperty("testValidationOnly")){
                                    logger.info("The 'testValidationOnly' flag is true, not going to drop the database.")
                                }
                                else {
                                    SqlUtils.dropDatabase(project, properties)
                                }
                            }
                            properties.interpolateCompositeProperties()
                        }
                        task.doLast {
                            properties.writeDbProps()
                        }
                    },
                dependsOn: [pickDbTask]
            )

            String undeployTaskName = "undeployModulesNotFor${properties.shortType.capitalize()}"
            Task undeployTask = project.tasks.findByName(undeployTaskName)
            if (undeployTask == null)
            {
                undeployTask = project.task(undeployTaskName,
                        group: GroupNames.DEPLOY,
                        description: "Undeploy modules that are either not supposed to be built or are not supported by database ${properties.dbTypeAndVersion}",
                        type: UndeployModules,
                        {UndeployModules task ->
                            task.dbType = properties.shortType
                        }
                )
                undeployTask.mustRunAfter(project.project(":server").tasks.pickMSSQL)
                undeployTask.mustRunAfter(project.project(":server").tasks.pickPg)
                project.tasks.startTomcat.mustRunAfter(undeployTask)
            }

            project.project(":server:test").tasks.startTomcat.mustRunAfter(setUpDbTask)
            Task ciTestTask = project.task("ciTests" + properties.dbTypeAndVersion.capitalize(),
                    group: GroupNames.TEST_SERVER,
                    description: "Run a test suite for ${properties.dbTypeAndVersion} on the TeamCity server",
                    type: RunTestSuite,
                    dependsOn: [setUpDbTask, undeployTask],
                    { RunTestSuite task ->
                        task.dbProperties = properties
                    }
            )
            ciTests.add(ciTestTask)
            ciTestTask.mustRunAfter(project.tasks.validateConfiguration)
            ciTestTask.mustRunAfter(project.tasks.cleanTestLogs)
            ciTestTask.mustRunAfter(project.tasks.startTomcat)
        }

        project.task("ciTests",
            group: GroupNames.TEST_SERVER,
            dependsOn: ciTests + project.tasks.validateConfiguration,
            description: "Run a test suite on the TeamCity server",
                {
                    doLast
                    {
                        killFirefox(project)
                    }
                })
        project.tasks.ciTests.dependsOn(project.tasks.startTomcat)
        project.tasks.ciTests.dependsOn(project.tasks.cleanTestLogs)
        project.tasks.startTomcat.mustRunAfter(project.tasks.cleanTestLogs)
    }

    private static void killChrome(Project project)
    {
        if (SystemUtils.IS_OS_WINDOWS)
        {
            project.ant.exec(executable: "taskkill")
                    {
                        arg(line:"/F /IM chromedriver.exe" )
                    }
            project.ant.exec(executable: "taskkill")
                    {
                        arg(line:"/F /IM chrome.exe" )
                    }
        }
        else if (SystemUtils.IS_OS_UNIX)
        {
            project.ant.exec(executable: "killall")
                    {
                        arg(line:  "-q -KILL chromedriver")
                    }
            project.ant.exec(executable: "killall")
                    {
                        arg(line: "-q -KILL chrome")
                    }
            project.ant.exec(executable: "killall")
                    {
                        arg(line: "-q KILL BrowserBlocking")
                    }
        }
    }

    private static void killFirefox(Project project)
    {
        if (SystemUtils.IS_OS_WINDOWS)
        {
            project.ant.exec(executable: "taskkill")
                    {
                        arg(line: "/F /IM firefox.exe")
                    }
        }
        else if (SystemUtils.IS_OS_UNIX)
        {
            project.ant.exec(executable: "killall")
                    {
                        arg(line: "-q firefox")
                    }
        }
    }


    private void ensureShutdown(Project project)
    {
        String debugPort = extension.getTeamCityProperty("tomcat.debug")
        if (!debugPort.isEmpty())
        {
            project.logger.debug("Ensuring shutdown using port ${debugPort}")
            project.javaexec({ JavaExecSpec spec ->
                spec.main = "org.labkey.test.debug.ThreadDumpAndKill"
                spec.classpath { [project.sourceSets.debug.output.classesDir, project.configurations.debugCompile] }
                spec.args = [debugPort]
                spec.ignoreExitValue = true
            })
        }
    }
}

