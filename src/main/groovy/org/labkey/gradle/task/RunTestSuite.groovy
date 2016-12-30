package org.labkey.gradle.task

import org.gradle.api.file.CopySpec
import org.labkey.gradle.plugin.TeamCity
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
        super()
        setSystemProperties()

        scanForTestClasses = false
        include "org/labkey/test/Runner.class"

        dependsOn(project.tasks.writeSampleDataFile)

        dependsOn(project.tasks.ensurePassword)
        if (project.findProject(":tools:Rpackages:install") != null)
            dependsOn(project.project(':tools:Rpackages:install'))
        if (!project.getPlugins().hasPlugin(TeamCity.class))
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

    private void setSystemProperties()
    {
        super.setSystemProperties()
        if (project.hasProperty('teamcity'))
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

        }
    }
}
