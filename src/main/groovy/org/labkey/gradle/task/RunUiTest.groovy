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
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.TomcatExtension
import org.labkey.gradle.plugin.extension.UiTestExtension
/**
 * Class that sets up jvmArgs and our standard output options
 */
class RunUiTest extends Test
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
            reports.junitXml.enabled = false
            reports.junitXml.setDestination( new File("${project.buildDir}/${LOG_DIR}/reports/xml"))
            reports.html.enabled = true
            reports.html.setDestination(new File( "${project.buildDir}/${LOG_DIR}/reports/html"))
        }
        setClasspath (project.sourceSets.uiTest.runtimeClasspath)
        setTestClassesDirs (project.sourceSets.uiTest.output.classesDirs)

        ignoreFailures = true // Failing tests should not cause task to fail
        outputs.upToDateWhen( { return false }) // always run tests when asked to
    }

    protected String getDebugPort()
    {
        return testExt.getTestConfig("selenium.debug.port")
    }

    void setJvmArgs()
    {
        List<String> jvmArgsList = ["-Xmx512m",
                                    "-Xdebug",
                                    "-Xrunjdwp:transport=dt_socket,server=y,suspend=${testExt.getTestConfig("debugSuspendSelenium")},address=${getDebugPort()}",
                                    "-Dfile.encoding=UTF-8"]

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
        systemProperty "failure.output.dir", "${project.buildDir}/${LOG_DIR}"
        systemProperty "labkey.root", project.rootProject.projectDir
        systemProperty "project.root", project.rootProject.projectDir

        systemProperty "user.home", System.getProperty('user.home')
        systemProperty "tomcat.home", project.ext.tomcatDir
        systemProperty "test.credentials.file", "${project.projectDir}/test.credentials.json"
        if (project.findProject(":server:test") != null)
        {
            Project testProject = project.project(":server:test")
            if (SystemUtils.IS_OS_WINDOWS)
            {
                if (SystemUtils.OS_ARCH.equals("amd64"))
                    systemProperty "webdriver.ie.driver", "${testProject.projectDir}/bin/windows/amd64/IEDriverServer.exe"
                else if (SystemUtils.OS_ARCH.equals("i386"))
                    systemProperty "webdriver.ie.driver", "${testProject.projectDir}/bin/windows/i386/IEDriverServer.exe"
                systemProperty "webdriver.chrome.driver", "${testProject.projectDir}/bin/windows/chromedriver.exe"
            }
            else if (SystemUtils.IS_OS_MAC)
            {
                systemProperty "webdriver.chrome.driver", "${testProject.projectDir}/bin/mac/chromedriver"
            }
            else if (SystemUtils.IS_OS_LINUX)
            {
                if (SystemUtils.OS_ARCH.equals("amd64"))
                    systemProperty "webdriver.chrome.driver", "${testProject.projectDir}/bin/linux/amd64/chromedriver"
                else if (SystemUtils.OS_ARCH.equals("i386"))
                    systemProperty "webdriver.chrome.driver", "${testProject.projectDir}/bin/linux/i386/chromedriver"
            }
        }
    }

}
