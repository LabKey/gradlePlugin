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
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.DeleteSpec
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Delete
import org.labkey.gradle.plugin.extension.DistributionExtension
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.TeamCityExtension
import org.labkey.gradle.task.ClientApiDistribution
import org.labkey.gradle.task.ModuleDistribution
import org.labkey.gradle.task.PipelineConfigDistribution
import org.labkey.gradle.task.PomFile
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames

class Distribution implements Plugin<Project>
{
    public static final String DISTRIBUTION_GROUP = "org.labkey.distribution"

    @Override
    void apply(Project project)
    {
        project.group = DISTRIBUTION_GROUP
        project.extensions.create("dist", DistributionExtension, project)
        // We add the TeamCity extension here if it doesn't exist because we will use the build
        // number property from TeamCity in the distribution artifact names, if present.
        TeamCityExtension teamCityExt  = project.getExtensions().findByType(TeamCityExtension.class)
        if (teamCityExt == null)
            project.extensions.create("teamCity", TeamCityExtension, project)

        // we depend on tasks from the server project, so it needs to have been evaluated first
        project.evaluationDependsOn(":server")
        addConfigurations(project)
        addTasks(project)
        addTaskDependencies(project)
        if (BuildUtils.shouldPublishDistribution(project))
            addArtifacts(project)
    }

    private void addConfigurations(Project project)
    {
        project.configurations
                {
                    distribution
                }
    }

    private static void addTasks(Project project)
    {
        project.task(
                'cleanDist',
                group: GroupNames.DISTRIBUTION,
                type: Delete,
                description: "Removes the distributions directory ${project.dist.dir}",
                { DeleteSpec spec ->
                    spec.delete project.dist.dir
                }
        )
        project.task(
                'clean',
                group: GroupNames.BUILD,
                type: Delete,
                description: "Removes the distribution build directory ${project.buildDir} and distribution directory ${project.dist.dir}/${project.name}",
                {
                    DeleteSpec spec ->
                        spec.delete project.buildDir
                        spec.delete "${project.dist.dir}/${project.name}"
                }
        )
    }

    private static void addTaskDependencies(Project project)
    {
        // This block sets up the task dependencies for each configuration dependency.
        project.afterEvaluate {
            if (project.hasProperty("distribution"))
            {
                Task distTask = project.tasks.distribution
                project.configurations.distribution.dependencies.each {
                    if (it instanceof DefaultProjectDependency)
                    {
                        DefaultProjectDependency dep = (DefaultProjectDependency) it
                        if (dep.dependencyProject.tasks.findByName("module") != null)
                            distTask.dependsOn(dep.dependencyProject.tasks.module)
                    }
                }
            }
        }
    }


    /**
     * This method is used within the distribution build.gradle files to allow distributions
     * to easily build upon one another.
     * @param project the project that is to inherit dependencies
     * @param inheritedProjectPath the project whose dependencies are inherited
     * @param list of paths for modules that are to be included from the set of inherited modules (e.g., [":server:modules:search"])
     */
    static void inheritDependencies(Project project, String inheritedProjectPath, List<String> excludedModules=[])
    {
        // Unless otherwise indicated, projects are evaluated in alphanumeric order, so
        // we explicitly indicate that the project to be inherited from must be evaluated first.
        // Otherwise, there will be no dependencies to inherit.
        project.evaluationDependsOn(inheritedProjectPath)
        project.project(inheritedProjectPath).configurations.distribution.dependencies.each {
            Dependency dep ->
                if (dep instanceof ModuleDependency)
                    project.dependencies.add("distribution", dep)
                else if (dep instanceof ProjectDependency && !excludedModules.contains(dep.dependencyProject.path))
                    project.dependencies.add("distribution", dep)

        }
    }

    private void addArtifacts(Project project)
    {
        project.apply plugin: 'maven'
        project.apply plugin: 'maven-publish'

        project.afterEvaluate {
            String artifactId = getArtifactId(project)
            project.task("pomFile",
                    group: GroupNames.PUBLISHING,
                    description: "create the pom file for this project",
                    type: PomFile,
                    {PomFile pomFile ->
                        pomFile.artifactCategory = "distributions"
                        pomFile.pomProperties = LabKeyExtension.getBasePomProperties(artifactId, project.dist.description)
                    }
            )
            project.publishing {
                publications {
                    distributions(MavenPublication) { pub ->
                        pub.artifactId(artifactId)
                        project.tasks.each {
                            if (it instanceof ModuleDistribution ||
                                    it instanceof ClientApiDistribution ||
                                    it instanceof PipelineConfigDistribution)
                            {
                                it.outputs.files.each {File file ->
                                    pub.artifact(file)
                                    {
                                        String fileName = file.getName()
                                        if (fileName.endsWith("gz"))
                                            extension "tar.gz"
                                        if (fileName.contains("-src."))
                                            classifier "src"
                                        else if (fileName.contains(ClientApiDistribution.XML_SCHEMA_DOC))
                                            classifier ClientApiDistribution.SCHEMA_DOC_CLASSIFIER
                                        else if (fileName.contains(ClientApiDistribution.CLIENT_API_JSDOC))
                                            classifier ClientApiDistribution.JSDOC_CLASSIFIER
                                    }
                                }
                            }
                        }
                    }
                }

                project.artifactoryPublish {
                    project.tasks.each {
                        if (it instanceof ModuleDistribution ||
                                it instanceof ClientApiDistribution ||
                                it instanceof PipelineConfigDistribution)
                        {
                            dependsOn it
                        }
                    }
                    dependsOn project.tasks.pomFile
                    publications('distributions')
                }
            }
        }
    }

    private static String getArtifactId(Project project)
    {
        if (project.dist.artifactId != null)
            return project.dist.artifactId
        else if (project.tasks.findByName("distribution") != null)
        {
            if (project.tasks.distribution instanceof ModuleDistribution)
                return ((ModuleDistribution) project.tasks.distribution).getArtifactId()
        }
        return project.name
    }

}


