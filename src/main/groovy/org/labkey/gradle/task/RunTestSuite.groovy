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
import org.gradle.api.tasks.Internal
import org.labkey.gradle.plugin.TeamCity
import org.labkey.gradle.plugin.extension.TeamCityExtension
import org.labkey.gradle.util.DatabaseProperties

/**
 * Class that sets our test/Runner.class as the junit test suite and configures a bunch of system properties for
 * running these suites of tests.
 */
abstract class RunTestSuite extends RunUiTest
{
    // Designated as @Internal instead of @Input to avoid this error in TeamCity (dbProperties is not used for running local tests).
    //    [10:00:41][Gradle failure report] Execution failed for task ':server:testAutomation:ciTestsSqlserver2019'.
    //    [10:00:41][Gradle failure report] > Unable to store input properties for task ':server:testAutomation:ciTestsSqlserver2019'. Property 'dbProperties' with value 'org.labkey.gradle.util.DatabaseProperties@3a6453c6' cannot be serialized.
    // Not sure why it can't be serialized, but we don't really need this to participate in up-to-date checks
    @Internal
    DatabaseProperties dbProperties

    RunTestSuite()
    {
        scanForTestClasses = false
        include "org/labkey/test/Runner.class"
        dependsOn(project.tasks.writeSampleDataFile)

        dependsOn(project.tasks.ensurePassword)
        if (project.getPlugins().hasPlugin(TeamCity.class))
        {
            dependsOn(project.tasks.killChrome)
            dependsOn(project.tasks.killFirefox)
        }
    }

    protected void setTeamCityProperties()
    {
        if (TeamCityExtension.isOnTeamCity(project))
        {
            systemProperty "teamcity.tests.recentlyFailedTests.file", project.teamcity['teamcity.tests.recentlyFailedTests.file']
            systemProperty "teamcity.build.changedFiles.file", project.teamcity['teamcity.build.changedFiles.file']
            String runRiskGroupTestsFirst = project.teamcity['tests.runRiskGroupTestsFirst']
            if (runRiskGroupTestsFirst != null)
            {
                systemProperty "testNewAndModified", "${runRiskGroupTestsFirst.contains("newAndModified")}"
                systemProperty "testRecentlyFailed", "${runRiskGroupTestsFirst.contains("recentlyFailed")}"
            }
            systemProperty "teamcity.buildType.id", project.teamcity['teamcity.buildType.id']
            systemProperty "tomcat.port", project.teamcity["tomcat.port"]
            systemProperty "tomcat.debug", project.teamcity["tomcat.debug"]
            systemProperty "labkey.port", project.teamcity['tomcat.port']
            systemProperty "maxTestFailures", project.teamcity['maxTestFailures']
            systemProperty 'test.credentials.file', project.teamcity['test.credentials.file']
            systemProperty 'testValidationOnly', project.teamcity['testValidationOnly']

            Properties testConfig = testExt.getConfig()
            for (String key : testConfig.keySet())
            {
                if (!StringUtils.isEmpty((String) project.teamcity[key]))
                {
                    systemProperty key, project.teamcity[key]
                }
            }
            // Include all 'webtest' and 'webdriver' properties, whether they are in test.properties or not
            for (String key : project.ext.properties.keySet())
            {
                if (key.startsWith("webtest.") || key.startsWith("webdriver.")) {
                    systemProperty key, project.ext[key]
                }
            }
        }
    }
}
