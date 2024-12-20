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
package org.labkey.gradle.plugin.extension

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.labkey.gradle.plugin.ServerDeploy
import org.labkey.gradle.util.BuildUtils

class ServerDeployExtension
{
    Map<String, String> foundModules = new HashMap<>();

    @Deprecated(forRemoval=true)
    static String getServerDeployDirectory(Project project)
    {
        return getServerDeployDirectoryPath(Project project)
    }

    static String getServerDeployDirectoryPath(Project project)
    {
        return BuildUtils.getRootBuildDirFile(project, ServerDeploy.DEPLOY_DIR).path
    }

    static String getEmbeddedServerDeployDirectoryPath(Project project)
    {
        return "${getServerDeployDirectoryPath(project)}/embedded"
    }

    static Directory getEmbeddedDir(Project project)
    {
        return project.rootProject.layout.buildDirectory.dir(ServerDeploy.DEPLOY_DIR + "/embedded").get()
    }

    static Directory getEmbeddedBinDir(Project project)
    {
        return project.rootProject.layout.buildDirectory.dir(ServerDeploy.DEPLOY_DIR + "/embedded/bin").get()
    }

    static String getModulesDeployDirectory(Project project)
    {
        return "${getServerDeployDirectoryPath(project)}/${ServerDeploy.MODULES_DIR}"
    }

    String getFoundModule(String key)
    {
        return foundModules.get(key)
    }

    void addFoundModule(String key, String path)
    {
        foundModules.put(key, path)
    }

    void removeFoundModule(String key)
    {
        foundModules.remove(key)
    }
}
