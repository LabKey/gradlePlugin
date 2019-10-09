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
package org.labkey.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.StagingExtension
import org.labkey.gradle.util.ModuleFinder
/**
 * Defines a set of extension properties for ease of reference. This also adds a two extensions
 * for some basic properties.
 * Created by susanh on 4/13/16.
 */
class LabKey implements Plugin<Project>
{
    public static final String API_GROUP = "org.labkey"
    public static final String MODULE_GROUP = "org.labkey.module"
    public static final String CLIENT_LIBS_CLASSIFER = "web"
    public static final String SOURCES_CLASSIFIER = "sources"
    public static final String JAVADOC_CLASSIFIER = "javadoc"
    public static final String FAT_JAR_CLASSIFIER = "all"

    @Override
    void apply(Project project)
    {
        project.group = API_GROUP
        project.subprojects { Project subproject ->
            if (ModuleFinder.isDistributionProject(subproject))
                subproject.buildDir = "${project.rootProject.buildDir}/installer/${subproject.name}"
            else
                subproject.buildDir = "${project.rootProject.buildDir}/modules/${subproject.name}"

        }

        addConfigurations(project)

        LabKeyExtension labKeyExt = project.extensions.create("labkey", LabKeyExtension)
        labKeyExt.setDirectories(project)

        StagingExtension stagingExt = project.extensions.create("staging", StagingExtension)
        stagingExt.setDirectories(project)
    }

    // These configurations are used for deploying the app.  We declare them here
    // because we need them available for all projects to declare their dependencies
    // to these configurations.
    private static void addConfigurations(Project project)
    {
        project.configurations
                {
                    modules
                    jars
                    // we don't want this to be transitive because we use this configuration to
                    // clean out the tomcat/lib directory when we do a cleanDeploy and the transitive
                    // dependencies include some of the jars that are native to tomcat.
                    tomcatJars { transitive = false }
                    remotePipelineJars
                    external
                }
        project.configurations.external.setDescription("External dependencies to be included in a module's lib directory")
        project.configurations.jars.setDescription("Dependencies to be copied into the WEB-INF/lib directory for the deployed server")
        project.configurations.modules.setDescription("Modules used in the current server deployment")
        project.configurations.tomcatJars.setDescription("Dependencies to be copied into the tomcat/lib directory upon deployment")
        project.configurations.remotePipelineJars.setDescription("Dependencies required for running remote pipeline jobs")

    }
}



