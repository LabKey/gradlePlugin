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
import org.gradle.api.file.CopySpec
import org.labkey.gradle.plugin.TeamCity
import org.labkey.gradle.plugin.extension.TeamCityExtension
import org.labkey.gradle.util.DatabaseProperties
/**
 * Class that sets our test/Runner.class as the junit test suite and configures a bunch of system properties for
 * running these suites of tests.
 */
class RunTestSuite extends RunUiTest
{
    DatabaseProperties dbProperties

    RunTestSuite()
    {
        project.logger.info("RunTestSuite: constructor");
        scanForTestClasses = false
        include "org/labkey/test/Runner.class"
        if (project.findProject(":sampledata:qc") != null)
            dependsOn(project.project(":sampledata:qc").tasks.jar)
        dependsOn(project.tasks.writeSampleDataFile)

        dependsOn(project.tasks.ensurePassword)
        if (project.findProject(":tools:Rpackages:install") != null)
            dependsOn(project.project(':tools:Rpackages:install'))
        if (!project.getPlugins().hasPlugin(TeamCity.class) && project.tasks.findByName('packageChromeExtensions') != null)
            dependsOn(project.tasks.packageChromeExtensions)
        if (project.findProject(":tools:Rpackages:install") != null)
            dependsOn(project.project(':tools:Rpackages:install'))
        if (project.getPlugins().hasPlugin(TeamCity.class))
        {
            dependsOn(project.tasks.killChrome)
            dependsOn(project.tasks.ensurePassword)

            doLast( {
                project.copy({ CopySpec copy ->
                    copy.from "${project.tomcatDir}/logs"
                    copy.into "${project.buildDir}/logs/${dbProperties.dbTypeAndVersion}"
                })
            })
        }
    }

    protected void setTeamCityProperties()
    {
        project.logger.info("RunTestSuite: setTeamCityProperties");
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
            systemProperty "tomcat.home", project.teamcity["tomcat.home"]
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
        }
    }
}
