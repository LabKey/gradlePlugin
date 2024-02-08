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
import org.labkey.gradle.util.BuildUtils

class ServerDeployExtension
{
    String dir
    String embeddedDir
    String modulesDir
    String webappDir
    String binDir
    String pipelineLibDir
    Map<String, String> foundModules = new HashMap<>();

    static String getServerDeployDirectory(Project project)
    {
        return BuildUtils.getRootBuildDirFile(project, "deploy").path
    }

    static String getEmbeddedServerDeployDirectory(Project project)
    {
        return "${getServerDeployDirectory(project)}/embedded"
    }

    static String getModulesDeployDirectory(Project project)
    {
        return "${getServerDeployDirectory(project)}/modules"
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
