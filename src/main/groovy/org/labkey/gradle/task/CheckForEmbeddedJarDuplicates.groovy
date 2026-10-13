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
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.labkey.gradle.util.BuildUtils

import java.util.regex.Matcher

/**
 * Checks that none of the jar files included in the Spring Boot jar for the embedded server are also included,
 * in the same or a different version, in a module.
 */
@UntrackedTask(because="Should always be run")
abstract class CheckForEmbeddedJarDuplicates extends DefaultTask
{
    enum ConflictAction  {
        fail,
        warn
    }

    /** The jar files packaged into the Spring Boot jar. Usually this will be the productionRuntimeClasspath configuration. **/
    @InputFiles @PathSensitive(PathSensitivity.NAME_ONLY)
    abstract ConfigurableFileCollection getEmbeddedJars()

    /** The jar files included in modules **/
    @InputFiles @PathSensitive(PathSensitivity.ABSOLUTE)
    abstract ConfigurableFileCollection getModuleJars()

    /** Map from the absolute path of a directory in moduleJars to the module it belongs to, used for reporting **/
    @Internal
    abstract MapProperty<String, String> getModuleNames()

    /** Artifact names (including classifier) that are allowed to be in both the embedded server and a module **/
    @Input
    abstract SetProperty<String> getAllowedDuplicates()

    /** Indicates what should happen when a duplicate is detected. There is nothing safe to delete, so 'delete' is treated as 'fail'. **/
    @Input
    abstract Property<ConflictAction> getConflictAction()

    CheckForEmbeddedJarDuplicates()
    {
        allowedDuplicates.convention(Set.of())
        conflictAction.convention(project.hasProperty('embeddedJarConflictAction') ? ConflictAction.valueOf((String) project.property('embeddedJarConflictAction')) : ConflictAction.fail)
    }

    // Don't call this from a closure: closures dispatch dynamically against Gradle's generated
    // subclass of this task, and Groovy doesn't find private methods from a subclass instance.
    private static String getArtifactName(String fileName)
    {
        Matcher matcher = BuildUtils.VERSIONED_ARTIFACT_NAME_PATTERN.matcher(fileName)
        if (!matcher.matches())
            return null
        // we support artifacts with different classifiers (e.g., activeio-core-3.1.0-tests.jar is not a duplicate of activeio-core-3.1.0.jar)
        String name = matcher.group(BuildUtils.ARTIFACT_NAME_INDEX)
        if (matcher.group(BuildUtils.ARTIFACT_CLASSIFIER_INDEX) != null)
            name += matcher.group(BuildUtils.ARTIFACT_CLASSIFIER_INDEX)
        return name
    }

    @TaskAction
    void doAction()
    {
        Map<String, String> embeddedNames = new HashMap<>()
        for (File f : embeddedJars.files)
        {
            String name = f.name.endsWith(".jar") ? getArtifactName(f.name) : null
            if (name != null)
                embeddedNames.put(name, f.name)
        }

        Set<String> allowed = allowedDuplicates.get()
        Map<String, String> modules = moduleNames.get()
        List<String> conflictMessages = []
        for (File f : moduleJars.files.toSorted())
        {
            String name = f.name.endsWith(".jar") ? getArtifactName(f.name) : null
            if (name != null && embeddedNames.containsKey(name))
            {
                String module = modules.getOrDefault(f.parentFile.absolutePath, f.parentFile.path)
                if (allowed.contains(name))
                    this.logger.info("${f.name} in ${module} duplicates embedded server jar ${embeddedNames.get(name)}, but deemed acceptable.")
                else
                    conflictMessages += "${f.name} in ${module} duplicates embedded server jar ${embeddedNames.get(name)}."
            }
        }

        if (!conflictMessages.isEmpty())
        {
            String message = "Jar files included in the embedded server are also included in modules:\n  " + conflictMessages.join("\n  ") +
                    "\nExclude these from the module dependencies or add them to the allowed duplicates for this task."
            if (conflictAction.get() == ConflictAction.warn)
                this.logger.warn("WARNING: " + message)
            else
                throw new GradleException(message)
        }
    }
}
