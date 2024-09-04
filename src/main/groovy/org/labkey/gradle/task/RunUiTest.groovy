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
package org.labkey.gradle.task

import org.apache.commons.lang3.StringUtils
import org.gradle.api.tasks.testing.Test
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.TomcatExtension
import org.labkey.gradle.plugin.extension.UiTestExtension
import org.labkey.gradle.util.BuildUtils

/**
 * Class that sets up jvmArgs and our standard output options
 */
abstract class RunUiTest extends Test
{
    public static final String LOG_DIR = "test/logs"
    protected UiTestExtension testExt

    RunUiTest()
    {
        testLogging.showStandardStreams = true
        testExt = (UiTestExtension) project.getExtensions().getByType(UiTestExtension.class)
        setSystemProperties()
        setJvmArgs()

        reports { TestTaskReports -> reports
            reports.junitXml.required = false
            reports.junitXml.outputLocation = BuildUtils.getBuildDirFile(project, "${LOG_DIR}/reports/xml")
            reports.html.required = true
            reports.html.outputLocation = BuildUtils.getBuildDirFile(project, "${LOG_DIR}/reports/html")
        }
        setClasspath (project.sourceSets.uiTest.runtimeClasspath)
        setTestClassesDirs (project.sourceSets.uiTest.output.classesDirs)

        ignoreFailures = true // Failing tests should not cause task to fail
        outputs.upToDateWhen( { return false }) // always run tests when asked to
    }

    void setJvmArgs()
    {
        List<String> jvmArgsList = ["-Xmx512m",
                                    "-Xdebug",
                                    "-Xrunjdwp:transport=dt_socket,server=y," +
                                            "suspend=${testExt.getTestConfig("debugSuspendSelenium")}," +
                                            "address=${testExt.getTestConfig("selenium.debug.port")}",
                                    "-Dfile.encoding=UTF-8",
                                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                                    "--add-opens=java.base/java.util=ALL-UNNAMED"
        ]

        if (project.hasProperty("uiTestJvmOpts"))
            jvmArgsList.add((String) project.property("uiTestJvmOpts"))

        TomcatExtension tomcat = project.extensions.findByType(TomcatExtension.class)

        if (tomcat != null && !tomcat.trustStore.isEmpty() && !tomcat.trustStorePassword.isEmpty())
        {
            jvmArgsList += [tomcat.trustStore, tomcat.trustStorePassword]
        }

        jvmArgs jvmArgsList
    }

    protected void setSystemProperties()
    {
        Properties testConfig = testExt.getConfig()
        for (String key : testConfig.keySet())
        {
            if (!StringUtils.isEmpty((String) testConfig.get(key)))
                systemProperty key, testConfig.get(key)
        }
        systemProperty "devMode", LabKeyExtension.isDevMode(project)
        systemProperty "failure.output.dir", "${BuildUtils.getBuildDirPath(project)}/${LOG_DIR}"
        systemProperty "labkey.root", project.rootProject.projectDir
        systemProperty "project.root", project.rootProject.projectDir
        systemProperty "user.home", System.getProperty('user.home')
        // A handful of tests require tomcat.home to be defined when running within IntelliJ
        systemProperty "tomcat.home", project.tomcat.catalinaHome
        systemProperty "test.credentials.file", "${project.projectDir}/test.credentials.json"

        setTeamCityProperties()
    }

    protected void setTeamCityProperties() {
        // do nothing by default
    }
}
