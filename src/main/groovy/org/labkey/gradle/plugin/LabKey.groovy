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
import org.labkey.gradle.util.BuildUtils

/**
 * Defines a set of extension properties for ease of reference. This also adds a two extensions
 * for some basic properties.
 */
class LabKey implements Plugin<Project>
{
    public static final String SOURCES_CLASSIFIER = "sources"
    public static final String JAVADOC_CLASSIFIER = "javadoc"
    public static final String FAT_JAR_CLASSIFIER = "all"

    @Override
    void apply(Project project)
    {
        if (project.hasProperty('includeVcs'))
        {
            if (project.hasProperty('nemerosaVersioningPluginVersion'))
                project.apply plugin: 'net.nemerosa.versioning'
            else
                project.apply plugin: 'org.labkey.versioning'
        }

        project.group = LabKeyExtension.LABKEY_GROUP
        project.version = BuildUtils.getVersionNumber(project)
        project.subprojects { Project subproject ->
            if (ModuleFinder.isDistributionProject(subproject))
                subproject.layout.buildDirectory = project.rootProject.layout.buildDirectory.file("installer/${subproject.name}")
            else
                subproject.layout.buildDirectory = project.rootProject.layout.buildDirectory.file("modules/${subproject.name}")
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
                    remotePipelineJars
                    external {
                        canBeConsumed = true
                        canBeResolved = true
                    }
                    externalsNotTrans {
                        transitive = false
                    }
                }
        project.configurations.external.setDescription("External dependencies to be included in a module's lib directory")
        project.configurations.externalsNotTrans.setDescription("Direct external dependencies (not including transitive dependencies), for use in creating jars.txt file")
        project.configurations.modules.setDescription("Modules used in the current server deployment")
        project.configurations.remotePipelineJars.setDescription("Dependencies required for running remote pipeline jobs")

    }
}



