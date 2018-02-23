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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.labkey.gradle.plugin.extension.UiTestExtension
import org.labkey.gradle.task.RunUiTest
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames
/**
 * Created by susanh on 12/21/16.
 */
class UiTest implements Plugin<Project>
{
    UiTestExtension testRunnerExt

    public static final String TEST_SRC_DIR = "test/src"
    public static final String TEST_RESOURCES_DIR = "test/resources";

    static Boolean isApplicable(Project project)
    {
        // For now we rely on the enableUiTests property here to allow use of individual test running
        // from the command line because the server/test project references the test/src directory as well
        // and IntelliJ doesn't like it when two projects reference the same source.

        // TODO we might be able to get rid of the dependency on the :server:test project if we publish the test jar,
        // but some modules probably reach into the server/test directory in undocumented ways.
        return project.hasProperty("enableUiTests") && project.file(TEST_SRC_DIR).exists() && project.findProject(":server:test") != null
    }

    @Override
    void apply(Project project)
    {
        testRunnerExt = project.extensions.create("uiTest", UiTestExtension, project)
        addSourceSets(project)
        addConfigurations(project)
        addDependencies(project)
        addTasks(project)
        addArtifacts(project)
    }

    protected void addConfigurations(Project project)
    {
        project.configurations {
            uiTestCompile.extendsFrom(compile)
        }
    }

    protected void addSourceSets(Project project)
    {
        project.sourceSets {
            uiTest {
                java {
                    srcDirs = [TEST_SRC_DIR]
                }
                resources {
                    srcDirs = [TEST_RESOURCES_DIR]
                }
            }
        }
    }

    protected void addDependencies(Project project)
    {
        if (project.path != ":server:test")
            BuildUtils.addLabKeyDependency(project: project, config: 'uiTestCompile', depProjectPath: ":server:test", depVersion: project.labkeyVersion)

        BuildUtils.addLabKeyDependency(project: project, config: 'uiTestCompile', depProjectPath: ":schemas", depVersion: project.labkeyVersion)
        BuildUtils.addLabKeyDependency(project: project, config: 'uiTestCompile', depProjectPath: ":server:api", depVersion: project.labkeyVersion)
        BuildUtils.addLabKeyDependency(project: project, config: 'uiTestCompile', depProjectPath: ":remoteapi:java", depVersion: project.labkeyVersion)
    }

    protected void addTasks(Project project)
    {
        project.task("uiTests",
                group: GroupNames.VERIFICATION,
                description: "Run UI (Selenium) tests for this module",
                type: RunUiTest
        )
        project.tasks.uiTests.mustRunAfter(project.project(":server").tasks.pickPg)
        project.tasks.uiTests.mustRunAfter(project.project(":server").tasks.pickMSSQL)
    }

    protected void addArtifacts(Project project)
    {
        // nothing to do here for the base case.
    }
}

