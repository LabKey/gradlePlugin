/*
 * Copyright (c) 2017-2026 LabKey Corporation
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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.labkey.gradle.plugin.LabKey

import java.nio.charset.StandardCharsets

/**
 * This task will collect all the resolved dependencies from each project and print a report
 * that shows the external dependencies with more than one version referenced within the build.
 * Each project's dependencies are resolved by its own {@link ListExternalDependencies} task, since a task may not
 * resolve configurations of other projects.
 */
@UntrackedTask(because="Output is logging")
abstract class ShowDiscrepancies extends DefaultTask
{
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    abstract ConfigurableFileCollection getDependencyReports()

    ShowDiscrepancies()
    {
        ConfigurableFileCollection reports = getDependencyReports()
        project.allprojects {
            Project p ->
                p.plugins.withType(LabKey) {
                    reports.from(p.tasks.named(LIST_EXTERNAL_DEPENDENCIES_TASK))
                }
        }
    }

    @TaskAction
    void show()
    {
        // org.apache:commons-collections -> 3.2 -> [:server:modules:query, :server:api]
        Map<String, Map<String, List<String>>> externals = new TreeMap<>()
        for (File report : dependencyReports.files)
        {
            List<String> lines = report.readLines(StandardCharsets.UTF_8.name())
            if (lines.isEmpty())
                continue
            String projectPath = lines.get(0)
            for (String coordinates : lines.subList(1, lines.size()))
            {
                int versionStart = coordinates.lastIndexOf(':')
                String artifact = coordinates.substring(0, versionStart)
                String version = coordinates.substring(versionStart + 1)
                List<String> paths = externals.computeIfAbsent(artifact, { new TreeMap<>() })
                        .computeIfAbsent(version, { new ArrayList<>() })
                if (!paths.contains(projectPath))
                    paths.add(projectPath)
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
