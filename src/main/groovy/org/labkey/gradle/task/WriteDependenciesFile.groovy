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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.tasks.*
import org.labkey.gradle.plugin.extension.ModuleExtension
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.ExternalDependency

import java.nio.charset.StandardCharsets

// This task can no longer be cacheable since we don't currently declare the jars.txt file as an output file.
// Once there are no manually maintained jars.txt files, this can be a cacheable task
//@CacheableTask
class WriteDependenciesFile extends DefaultTask
{
    // we assume that if a version number has changed, we should generate a new dependencies file
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFile
    File globalProperties = project.rootProject.file("gradle.properties")

    @OutputFile
    File dependenciesFile = project.file("resources/credits/dependencies.txt")

//    @OutputFile  Not declared as an output file currently since it may be manually maintained.
    private File jarsTxtFile = project.file("resources/credits/jars.txt")

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
        // add a configuration that has the external dependencies but is not transitive
        project.configurations {
            externalsNotTrans.extendsFrom(project.configurations.external)
            externalsNotTrans {
                transitive = false
            }
        }
        project.configurations.externalsNotTrans.setDescription("Direct external dependencies (not including transitive dependencies)")
        onlyIf {
            !project.configurations.externalsNotTrans.isEmpty()
        }
    }

    private void writeDependencies(String configurationName, OutputStreamWriter writer)
    {
        Configuration configuration = project.configurations.findByName(configurationName)
        if (configuration == null)
            return

        ModuleExtension extension = project.extensions.findByType(ModuleExtension.class)
        List<String> missing = []
        List<String> licenseMissing = []
        configuration.resolvedConfiguration.resolvedArtifacts.forEach {
            ResolvedArtifact artifact ->
                String versionString = artifact.moduleVersion.toString();
                if (!StringUtils.isEmpty(artifact.getClassifier()))
                    versionString += ":" + artifact.getClassifier()
                ExternalDependency dep = extension.getExternalDependency(versionString)
                if (dep) {
                    List<String> parts = new ArrayList<>()
                    parts.add(artifact.file.getName())
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
                        licenseMissing.add(artifact.moduleVersion.toString())
                    }
                    parts.add(dep.getPurpose() == null ? "" : dep.getPurpose())
                    writer.write("${parts.join("|")}\n");
                } else {
                    missing.add(artifact.moduleVersion.toString())
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
        ModuleExtension extension = project.extensions.findByType(ModuleExtension.class)
        if (extension.getExternalDependencies().isEmpty())
            return;

        OutputStreamWriter writer = null
        try {
            writer = new OutputStreamWriter(new FileOutputStream(jarsTxtFile), StandardCharsets.UTF_8)
            writer.write("{table}\n")
            writer.write("Filename|Component|Source|License|Purpose\n")
            writeDependencies("externalsNotTrans", writer)
            writeDependencies("creditable", writer)
            writer.write("{table}\n")
        }
        finally
        {
            if (writer != null)
                writer.close()
        }
    }

    // TODO this can go away if we change all modules to use the build-generated jars.txt file
    void writeDependenciesFile()
    {
        FileOutputStream outputStream = null;
        try
        {
            boolean isApi = project.path.equals(BuildUtils.getApiProjectPath(project.gradle))

            outputStream = new FileOutputStream(dependenciesFile)
            if (isApi)
                outputStream.write("# direct external dependencies of ${project.path} and dependencies of labkey-client-api\n".getBytes())
            else
                outputStream.write("# direct external dependencies for project ${project.path}\n".getBytes())

            Set<String> dependencySet = new HashSet<>();
            project.configurations.externalsNotTrans
                    .each { File file ->
                        outputStream.write((file.getName() + "\n").getBytes());
                        dependencySet.add(file.getName());
                    }
            if (isApi) {
                if (project.configurations.findByName("creditable") != null)
                {
                    project.configurations.creditable.each {
                        if (!dependencySet.contains(it.getName()))
                        {
                            outputStream.write((it.getName() + "\n").getBytes())
                            dependencySet.add(it.getName())
                        }
                    }
                }
            }
        }
        finally
        {
            if (outputStream != null)
                outputStream.close()
        }
    }

    @TaskAction
    void writeFiles()
    {
        this.writeDependenciesFile()
        this.writeJarsTxt()
    }
}
