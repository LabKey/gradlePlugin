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

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.java.archives.Manifest
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Jar
import org.labkey.gradle.plugin.extension.DistributionExtension
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.TeamCityExtension
import org.labkey.gradle.task.ModuleDistribution
import org.labkey.gradle.util.PomFileHelper
import org.labkey.gradle.util.GroupNames
import org.labkey.gradle.util.BuildUtils

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
        if (TeamCityExtension.isOnTeamCity(project) && teamCityExt == null)
            project.extensions.create("teamCity", TeamCityExtension, project)

        // we depend on tasks from the server project, so it needs to have been evaluated first
        project.evaluationDependsOn(BuildUtils.getServerProjectPath(project.gradle))
        // we also depend on the jar task from the embedded project, if available
        if (BuildUtils.useEmbeddedTomcat(project))
            project.evaluationDependsOn(BuildUtils.getEmbeddedProjectPath(project.gradle))
        // for non-open-source distributions, we depend on a task from the api project. No need to di things differently for open source, though.
        project.evaluationDependsOn(BuildUtils.getApiProjectPath(project.gradle))
        addConfigurations(project)
        addDependencies(project)
        addTasks(project)
        addTaskDependencies(project)

        // commented out until we start publishing distribution artifacts, and then we'll examine the publications more closely
//        if (BuildUtils.shouldPublishDistribution(project))
//            addArtifacts(project)
    }

    private void addConfigurations(Project project)
    {
        project.configurations
                {
                    distribution
                    extJsCommercial
                }
        project.configurations.distribution.setDescription("Artifacts of creating a LabKey distribution (aka installer)")
        project.configurations.extJsCommercial.setDescription("extJs commercial license libraries")

        if (project.configurations.findByName("utilities") == null)
        {
            project.configurations
                    {
                        utilities
                    }
            project.configurations.utilities.setDescription("Utility binaries for use on Windows platform")
        }
    }


    private void addDependencies(Project project)
    {
        // we package these Windows utilities with each distribution so any distribution can be used on any platform
        if (project.hasProperty('windowsUtilsVersion'))
            project.dependencies {
                utilities "org.labkey.tools.windows:utils:${project.windowsUtilsVersion}@zip"
            }
        if (!BuildUtils.isOpenSource(project)) {
            project.dependencies {
                extJsCommercial "com.sencha.extjs:extjs:4.2.1:commercial@zip"
                extJsCommercial "com.sencha.extjs:extjs:3.4.1:commercial@zip"
            }
        }
    }

    private static void addTasks(Project project)
    {
        project.tasks.register('cleanDist', Delete) {
            Delete task ->
                task.group = GroupNames.DISTRIBUTION
                task.description = "Removes the distributions directory ${project.dist.dir}"
                task.configure({ DeleteSpec spec ->
                    spec.delete project.dist.dir
                })
        }

        project.tasks.register('clean', Delete) {
            Delete task ->
                task.group = GroupNames.BUILD
                task.description = "Removes the distribution build directory ${project.buildDir} and distribution directory ${project.dist.dir}/${project.name}"
                task.configure ({
                    DeleteSpec spec ->
                        spec.delete project.buildDir
                        spec.delete "${project.dist.dir}/${project.name}"
                })
        }

        if (!BuildUtils.isOpenSource(project)) {
            project.tasks.register('patchApiModule', Jar) {
                Jar jar ->
                    jar.group = GroupNames.MODULE
                    jar.description = "Patches the api module to replace ExtJS libraries with commercial versions"
                    Project apiProject = project.project(BuildUtils.getApiProjectPath(project.gradle))
                    jar.archiveBaseName.set(apiProject.name)
                    jar.archiveVersion.set(project.getVersion().toString())
                    jar.archiveClassifier.set("extJsCommercial")
                    jar.archiveExtension.set('module')
                    jar.destinationDirectory = project.file("${project.rootProject.buildDir}/installer/patchApiModule")
                    jar.outputs.cacheIf({ true })
                    // first include the ext-3.4.1 and ext-4.2.1 directories from the extjs configuration artifacts
                    jar.into('web') {
                        from project.configurations.extJsCommercial.collect {
                            project.zipTree(it)
                        }
                    }
                    // include the original module file ...
                    jar.from(project.zipTree(apiProject.tasks.module.outputs.files.singleFile))
                    // ... but don't use the ext directories that come from that file
                    jar.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
                    FileModule.setJarManifestAttributes(apiProject, (Manifest) jar.manifest)
            }
        }
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
        project.apply plugin: 'maven-publish'

        // TODO this is really only an approximation of what's needed. We don't currently publish distribution artifacts
        // to artifactory
        project.afterEvaluate {
            String artifactId = getArtifactId(project)
            Properties pomProperties = LabKeyExtension.getApiPomProperties(artifactId, project.dist.description, project)
            project.publishing {
                publications {
                    distributions(MavenPublication) { pub ->
                        pub.artifactId(artifactId)
                        project.tasks.each {
                            if (it instanceof ModuleDistribution)
                            {
                                it.outputs.files.each {File file ->
                                    pub.artifact(file)
                                    {
                                        String fileName = file.getName()
                                        if (fileName.endsWith("gz"))
                                            extension "tar.gz"
                                        if (fileName.contains("-src."))
                                            classifier "src"
                                    }
                                }
                            }
                        }
                        pom {
                            name = project.name
                            description = pomProperties.getProperty("Description")
                            url = PomFileHelper.LABKEY_ORG_URL
                            developers PomFileHelper.getLabKeyTeamDevelopers()
                            // TODO this should probably not always be Apache license
//                            licenses pomUtil.getLicense()
                            organization PomFileHelper.getLabKeyOrganization()
//                            scm PomFileHelper.getLabKeyScm()
                            // doesn't seem like these pom files will have any dependencies
                        }
                    }
                }

                project.artifactoryPublish {
                    project.tasks.each {
                        if (it instanceof ModuleDistribution)
                        {
                            dependsOn it
                        }
                    }
                    publications('distributions')
                }
            }
        }
    }

    static String getArtifactId(Project project)
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


