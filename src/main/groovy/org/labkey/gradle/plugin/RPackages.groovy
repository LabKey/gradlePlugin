/*
 * Copyright (c) 2017 LabKey Corporation
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
import org.gradle.api.file.DeleteSpec
import org.gradle.api.tasks.Delete
import org.labkey.gradle.task.InstallRLabKey
import org.labkey.gradle.task.InstallRPackage
import org.labkey.gradle.task.InstallRuminex
import org.labkey.gradle.util.GroupNames
/**
 * Created by susanh on 3/15/17.
 */
class RPackages implements Plugin<Project>
{
    @Override
    void apply(Project project)
    {
        addTasks(project)
    }

    private static void addTasks(Project project)
    {
        String rLibsUserPath = InstallRPackage.getRLibsUserPath(project)
        project.tasks.register("clean", Delete) {
            Delete task ->
                task.group = GroupNames.DEPLOY
                task.description = "Delete user directory containing R libraries (${rLibsUserPath})"
                task.configure({
                    DeleteSpec delete ->
                        if (rLibsUserPath != null)
                            delete.delete rLibsUserPath
                })
        }

        project.tasks.register("installRLabKey", InstallRLabKey) {
            InstallRLabKey task ->
                task.group = GroupNames.DEPLOY
                task.description = "Install RLabKey"
        }

        project.tasks.register("installRuminex", InstallRuminex) {
            InstallRuminex task ->
                task.group = GroupNames.DEPLOY
                task.description = "Install Ruminex package"
                task.packageNames = ["Ruminex"]
                task.installScript = "install-ruminex-dependencies.R"
        }


        project.tasks.register("installFlowWorkspace", InstallRPackage) {
            InstallRPackage task ->
                task.group = GroupNames.DEPLOY
                task.description = "Install flow workspace package"
                task.packageNames = ["flowWorkspace"]
                task.installScript = "install-flowWorkspace.R"
        }

        project.tasks.register("installFlowStats", InstallRPackage) {
            InstallRPackage task ->
                task.group = GroupNames.DEPLOY
                task.description = "Install flowStats package"
                task.packageNames = ["flowStats"]
                task.installScript = "install-flowStats.R"
                task.dependsOn(project.tasks.installFlowWorkspace)
        }

        project.tasks.register("installKnitr",InstallRPackage) {
            InstallRPackage task ->
                task.group = GroupNames.DEPLOY
                task.description = "Install knitr package"
                task.packageNames = ["knitr", "rmarkdown"]
                task.installScript = "install-knitr.R"
        }

        project.tasks.register("installEhrDependencies",InstallRPackage) {
            InstallRPackage task ->
                task.group = GroupNames.DEPLOY
                task.description = "Install EHR Dependencies packages"
                task.packageNames = ["kinship2", "pedigree"]
                task.installScript = "install-ehr-dependencies.R"
        }

        project.tasks.register("installRSurvival", InstallRPackage) {
            InstallRPackage task ->
                task.group = GroupNames.DEPLOY
                task.description = "Install RSurvival package"
                task.packageNames = ["survival"]
                task.installScript = "install-survival.R"
        }

        project.tasks.register("install") {
            Task task ->
                task.group = GroupNames.DEPLOY
                task.description = "Install R packages"
                task.dependsOn(project.tasks.installRLabKey,
                        project.tasks.installRuminex,
                        project.tasks.installFlowStats,
                        project.tasks.installKnitr,
                        project.tasks.installEhrDependencies,
                        project.tasks.installRSurvival)
        }
    }

}
