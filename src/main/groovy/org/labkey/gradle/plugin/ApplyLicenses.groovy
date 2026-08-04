/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
import org.labkey.gradle.task.PatchApiModule
import org.labkey.gradle.task.VerifyLicensePatch
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames

class ApplyLicenses implements Plugin<Project>
{
    @Override
    void apply(Project project)
    {
        if (project.findProject(BuildUtils.getApiProjectPath(project.gradle)))
            project.evaluationDependsOn(BuildUtils.getApiProjectPath(project.gradle))
        addConfigurations(project)
        addDependencies(project)
        addTasks(project)
    }

    private void addConfigurations(Project project)
    {
        project.configurations
        {
            extJs3Commercial
            extJs4Commercial
            licensePatch {
                canBeConsumed = true
                canBeResolved = true
            }
        }
        project.configurations.extJs3Commercial.setDescription("extJs 3 commercial license libraries")
        project.configurations.extJs4Commercial.setDescription("extJs 4 commercial license libraries")
        project.configurations.licensePatch.setDescription("Modules that require patching with commercial-license libraries")
    }


    private void addDependencies(Project project)
    {
        if (!BuildUtils.isOpenSource(project)) {
            project.dependencies {
                // Can't have two versions of the same dependency in one configuration
                extJs4Commercial "com.sencha.extjs:extjs:4.2.1:commercial@zip"
                extJs3Commercial "com.sencha.extjs:extjs:3.4.1:commercial@zip"
            }

            BuildUtils.addLabKeyDependency(project, "licensePatch", BuildUtils.getApiProjectPath(project.gradle), "published", project.getVersion().toString(), "module")
        }
    }

    private static void addTasks(Project project)
    {
        if (!BuildUtils.isOpenSource(project)) {
            var patchApiTask = project.tasks.register('patchApiModule', PatchApiModule) {
                PatchApiModule jar ->
                    jar.group = GroupNames.DISTRIBUTION
                    jar.description = "Patches the api module to replace ExtJS libraries with commercial versions"
                    jar.archiveBaseName.set("api")
                    jar.archiveVersion.set(project.getVersion().toString())
                    jar.archiveClassifier.set("extJsCommercial")
                    jar.archiveExtension.set('module')
                    jar.destinationDirectory.set(project.layout.buildDirectory.dir("patchApiModule"))
                    jar.extJs3Archives.from(project.configurations.extJs3Commercial)
                    jar.extJs4Archives.from(project.configurations.extJs4Commercial)
                    jar.moduleArchives.from(project.configurations.licensePatch)
                    jar.manifest.attributes(
                            "Implementation-Version": project.version,
                            "Implementation-Title": "Internal API classes",
                            "Implementation-Vendor": "LabKey"
                    )
                    if (project.findProject(BuildUtils.getApiProjectPath(project.gradle))) {
                        var apiProj = project.project(BuildUtils.getApiProjectPath(project.gradle))
                        jar.dependsOn(apiProj.tasks.named("module"))
                        jar.archiveVersion.set(apiProj.getVersion().toString())
                    }
            }

            project.tasks.register('verifyLicensePatch', VerifyLicensePatch) {
                VerifyLicensePatch verify ->
                    verify.group = GroupNames.TEST
                    verify.description = "Verifies that the patched api module contains the commercial ExtJS license files"
                    verify.commercialArchives.from(project.configurations.extJs3Commercial, project.configurations.extJs4Commercial)
                    verify.patchedArchive.set(patchApiTask.flatMap { PatchApiModule jar -> jar.archiveFile })
            }
        }
    }
}
