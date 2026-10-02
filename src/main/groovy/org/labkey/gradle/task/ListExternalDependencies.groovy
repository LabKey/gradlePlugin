/*
 * Copyright (c) 2026 LabKey Corporation
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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.nio.charset.StandardCharsets

/**
 * Writes the external module versions resolved for a project's 'external' configuration to a file, with the project
 * path on the first line and one 'group:name:version' per line after that. These files are aggregated by the
 * {@link ShowDiscrepancies} task. The resolution happens in the context of the owning project, which is required
 * because a task may not resolve configurations of other projects.
 */
@CacheableTask
abstract class ListExternalDependencies extends DefaultTask
{
    @Input
    abstract Property<String> getProjectPath()

    @Input
    abstract ListProperty<String> getModuleVersions()

    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @TaskAction
    void list()
    {
        List<String> lines = [projectPath.get()]
        lines.addAll(moduleVersions.get())
        outputFile.get().asFile.setText(lines.join("\n") + "\n", StandardCharsets.UTF_8.name())
    }

    /**
     * @param root the root of a resolved dependency graph
     * @return the sorted 'group:name:version' coordinates of all external modules in the graph
     */
    static List<String> getModuleVersions(ResolvedComponentResult root)
    {
        Set<String> coordinates = new TreeSet<>()
        Set<ResolvedComponentResult> visited = new HashSet<>()
        Deque<ResolvedComponentResult> toVisit = new ArrayDeque<>()
        toVisit.add(root)
        while (!toVisit.isEmpty())
        {
            ResolvedComponentResult component = toVisit.poll()
            if (!visited.add(component))
                continue
            if (component.id instanceof ModuleComponentIdentifier)
            {
                ModuleComponentIdentifier id = (ModuleComponentIdentifier) component.id
                coordinates.add("${id.group}:${id.module}:${id.version}".toString())
            }
            for (DependencyResult dependency : component.dependencies)
            {
                if (dependency instanceof ResolvedDependencyResult)
                    toVisit.add(((ResolvedDependencyResult) dependency).selected)
            }
        }
        return new ArrayList<>(coordinates)
    }
}
