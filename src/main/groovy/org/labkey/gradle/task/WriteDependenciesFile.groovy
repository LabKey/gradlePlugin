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
package org.labkey.gradle.task

import org.apache.commons.lang3.StringUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Transformer
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.*
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.labkey.gradle.util.ExternalDependency

import java.nio.charset.StandardCharsets
import java.util.stream.Collectors

abstract class WriteDependenciesFile extends DefaultTask
{
    // we assume that if a version number has changed, we should generate a new dependencies file
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFile
    final abstract RegularFileProperty globalProperties = project.objects.fileProperty().fileValue(project.rootProject.file("gradle.properties"))

    @OutputFile
    final abstract RegularFileProperty jarsTxtFile = project.objects.fileProperty().convention(project.layout.projectDirectory.file("resources/credits/jars.txt"))

    @Input
    final abstract MapProperty<String, ExternalDependency> externalDependencies = project.objects.mapProperty(String, ExternalDependency).convention(Collections.emptyMap())

    @Input
    abstract ListProperty<ComponentArtifactIdentifier> getArtifactIds()

    WriteDependenciesFile()
    {
        if (project.file("build.gradle").exists())
        {
            this.inputs.file(project.file("build.gradle"))
        }
        if (project.file("gradle.properties").exists())
        {
            this.inputs.file(project.file("gradle.properties"))
        }
        onlyIf {
            !externalDependencies.get().isEmpty()
        }
    }

    private void writeDependencies(OutputStreamWriter writer)
    {
        List<String> missing = []
        List<String> licenseMissing = []
        Map<String, ExternalDependency> dependencies = externalDependencies.get()
        getArtifactIds().get().forEach {
            ComponentArtifactIdentifier artifact ->
                String versionString = artifact.componentIdentifier
                if (artifact instanceof DefaultModuleComponentArtifactIdentifier && !StringUtils.isEmpty(artifact.name.classifier))
                    versionString += ":" + artifact.name.classifier
                ExternalDependency dep = dependencies.get(versionString)
                if (dep) {
                    List<String> parts = new ArrayList<>()
                    parts.add(artifact.fileName)
                    parts.add(dep.getComponent())
                    if (!StringUtils.isBlank(dep.getSource())) {
                        if (!StringUtils.isBlank(dep.getSourceURL()))
                            parts.add("{link:${dep.getSource()}|${dep.getSourceURL()}}")
                        else
                            parts.add(dep.getSource())
                    } else
                        parts.add("")
                    if (!StringUtils.isBlank(dep.getLicenseName())) {
                        if (!StringUtils.isBlank(dep.getLicenseURL()))
                            parts.add("{link:${dep.getLicenseName()}|${dep.getLicenseURL()}}")
                        else
                            parts.add(dep.getLicenseName())
                    } else {
                        licenseMissing.add(artifact.displayName)
                    }
                    parts.add(dep.getPurpose() == null ? "" : dep.getPurpose())
                    writer.write("${parts.join("|")}\n")
                } else {
                    missing.add(artifact.displayName)
                }
        }
        List<String> exceptionMsg = []
        if (!licenseMissing.isEmpty())
            exceptionMsg.add("The following dependencies are missing license information: ${licenseMissing.join(", ")}.")
        if (!missing.isEmpty())
            exceptionMsg.add("The following dependencies were not registered with addExternalDependency: ${missing.join(", ")}. You must register all or none of your external dependencies with this method.")
        if (!exceptionMsg.isEmpty())
            throw new GradleException(exceptionMsg.join("\n"))
    }

    void writeJarsTxt()
    {
        if (externalDependencies.get().isEmpty())
            return

        OutputStreamWriter writer = null
        try {
            writer = new OutputStreamWriter(new FileOutputStream(jarsTxtFile.get().asFile), StandardCharsets.UTF_8)
            writer.write("{table}\n")
            writer.write("Filename|Component|Source|License|Purpose\n")
            writeDependencies(writer)
            writer.write("{table}\n")
        }
        finally
        {
            if (writer != null)
                writer.close()
        }
    }

    @TaskAction
    void writeFiles()
    {
        this.writeJarsTxt()
    }


    // For reference: https://docs.gradle.org/8.1.1/userguide/incremental_build.html#sec:task_input_using_dependency_resolution_results
    static class IdExtractor
            implements Transformer<List<ComponentArtifactIdentifier>, Collection<ResolvedArtifactResult>>
    {
        @Override
        List<ComponentArtifactIdentifier> transform(Collection<ResolvedArtifactResult> artifacts) {
           return artifacts.stream().map(artifact -> {
               return artifact.getId()
           }).collect(Collectors.toList())
        }
    }
}
