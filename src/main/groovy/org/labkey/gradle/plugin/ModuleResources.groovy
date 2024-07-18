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

import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.ModuleExtension
import org.labkey.gradle.task.GzipAction
import org.labkey.gradle.task.WriteDependenciesFile

class ModuleResources
{
    private static final String DIR_NAME = "resources"

    static void addTasks(Project project)
    {
        project.tasks.register("writeDependenciesList", WriteDependenciesFile) {
            WriteDependenciesFile task ->
                task.description = "write a list of direct external dependencies that should be checked on the credits page"
                try {
                    project.configurations.named('externalsNotTrans') {
                        Configuration config ->
                            Provider<Set<ResolvedArtifactResult>> artifacts = config.getIncoming().getArtifacts().getResolvedArtifacts();
                            task.getArtifactIds().set(artifacts.map(new WriteDependenciesFile.IdExtractor()))
                    }
                    task.externalDependencies.set(project.extensions.findByType(ModuleExtension.class).getExternalDependencies())
                } catch (UnknownDomainObjectException ignore) {

                }
        }

        project.tasks.named("processModuleResources").configure {dependsOn(project.tasks.named('writeDependenciesList'))}
        project.tasks.named("processResources").configure {dependsOn(project.tasks.named('processModuleResources'))}
        project.tasks.clean {dependsOn(project.tasks.named('cleanWriteDependenciesList'))}
    }

    static void addSourceSet(Project project)
    {
        project.sourceSets
                {
                    module { SourceSet set ->
                        set.resources {
                            srcDirs = [DIR_NAME]
                            exclude "schemas/**/obsolete/**"
                        }
                        set.output.resourcesDir = project.labkey.explodedModuleDir
                    }
                }
        if (!LabKeyExtension.isDevMode(project))
        {
            GzipAction zipAction = new GzipAction()
            zipAction.extraExcludes = ["views/**"]
            project.tasks.named("processModuleResources").configure {doLast(zipAction)}
        }
    }
}
