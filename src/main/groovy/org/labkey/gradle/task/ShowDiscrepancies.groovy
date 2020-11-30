/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
package org.labkey.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.tasks.TaskAction

/**
 * This task will collect all the resolved dependencies from each project and print a report
 * that shows the external dependencies with more than one version referenced within the build.
 */
class ShowDiscrepancies extends DefaultTask
{

    @TaskAction
    void show()
    {
        // org.apache:commons-collections -> 3.2 -> [:server:modules:query, :server:api]
        Map<String, Map<String, List<String>>> externals = new HashMap<>()
        project.allprojects {
            Project p ->
                Configuration externalConfig = p.configurations.findByName('external')
                if (externalConfig != null)
                {
                    externalConfig.resolvedConfiguration.resolvedArtifacts.each {
                        ResolvedArtifact dep ->

                            ModuleVersionIdentifier id = dep.moduleVersion.getId()
                            String artifact = "${id.getGroup()}:${id.getName()}"
                            String version = id.getVersion()
                            Map<String, List<String>> artifactMap = externals.get(artifact)
                            if (artifactMap == null)
                            {
                                artifactMap = new HashMap<>()
                                externals.put(artifact, artifactMap)
                            }
                            if (artifactMap.get(version) == null)
                            {
                                artifactMap.put(version, new ArrayList<>())
                            }
                            List<String> paths = artifactMap.get(version)
                            if (!paths.contains(p.getPath()))
                                paths.add(p.getPath())
                    }
                }
        }
        // look for maps that have more than one version and report these
        for (Map.Entry<String, Map<String, List<String>>> entry : externals.entrySet())
        {
            if (entry.value.size() > 1)
            {
                this.logger.error("${entry.key} has ${entry.value.size()} versions as follows: ")
                for (Map.Entry<String, List<String>> versionEntry : entry.value.entrySet())
                {
                    this.logger.error("\t${versionEntry.key}\t${versionEntry.value}")
                }
            }

        }
    }
}
