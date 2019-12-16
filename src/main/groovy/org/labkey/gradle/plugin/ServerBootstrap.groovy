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

/**
 * Adds tasks for building the bootstrap jar file, copying it to the tomcat directory and creating the api file list
 * used during startup to remove unused jar files from the deployment.
 *
 * CONSIDER: Convert to a Sync type task from Gradle to do the removal of unused jar files
 */
class ServerBootstrap implements Plugin<Project>
{
    private static final String BOOTSTRAP_MAIN_CLASS = "org.labkey.bootstrap.ModuleExtractor"
    public static final String JAR_BASE_NAME = "labkeyBootstrap"

    @Override
    void apply(Project project)
    {
        project.apply plugin: 'java-base'
        addSourceSets(project)
        addDependencies(project)
        addTasks(project)
    }

    private void addSourceSets(Project project)
    {
        project.sourceSets {
            main {
                java {
                    srcDirs = ['src']
                }
            }
        }

    }

    private void addDependencies(Project project)
    {
        project.dependencies
                {
                    implementation "org.apache.tomcat:tomcat-api:${project.apacheTomcatVersion}"
                    implementation "org.apache.tomcat:tomcat-catalina:${project.apacheTomcatVersion}"
                    implementation "org.apache.tomcat:tomcat-juli:${project.apacheTomcatVersion}"
                    implementation "org.apache.tomcat:tomcat-util:${project.apacheTomcatVersion}"
                }
    }

    private void addTasks(Project project)
    {
        project.jar {
            baseName = JAR_BASE_NAME
        }
        project.processResources.enabled = false
        project.jar.manifest {
            attributes provider: 'LabKey'
            attributes 'Main-Class': BOOTSTRAP_MAIN_CLASS
        }
    }
}
