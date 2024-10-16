/*
 * Copyright (c) 2018 LabKey Corporation
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
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.util.BuildUtils

import java.util.regex.Matcher

/**
 * Checks for conflicts that may exist between a file collection and the files in an existing directory
 */
class CheckForVersionConflicts  extends DefaultTask
{
    enum ConflictAction  {
        delete,
        fail,
        warn
    }

    // This is not declared as an input directory because it will fail if the
    // directory does not first exist.  We could change it so a mkdir happens
    // before this task is run, but it's probably not so expensive to do the check
    // even when things don't change.
    /** The directory to check for existing files **/
    public File directory

    /** The extension of the files to look for.  Null indicates all files **/
    @Input
    String extension = null

    /** Indicates what should happen when a conflict is detected **/
    @Input
    public final abstract Property<ConflictAction> conflictAction = project.objects.property(ConflictAction).convention(
            project.hasProperty('versionConflictAction') ? ConflictAction.valueOf((String) project.property('versionConflictAction')) : ConflictAction.fail)

    /** The collection of files to check for.  Usually this will come from a configuration. **/
    @InputFiles
    FileCollection collection

    /** The name of a task to run if conflicts are found that will resolve the conflict (presumably by cleaning out the directory) **/
    @Input
    String cleanTask

    ConflictAction getConflictAction() {
        return conflictAction.get()
    }

    @TaskAction
    void doAction()
    {
        List<String> conflictMessages = []
        Boolean haveMultiples = false
        Set<File> existingFilesInConflict = []

        Map<String, Tuple2<String, File>> nameVersionMap = new HashMap<>()
        File[] existingFiles = directory.listFiles(new FilenameFilter() {
            @Override
            boolean accept(File dir, String name) {
                return extension == null || name.endsWith(extension)
            }
        })
        for (File dFile: existingFiles) {
            Matcher matcher = BuildUtils.VERSIONED_ARTIFACT_NAME_PATTERN.matcher(dFile.name)
            if (matcher.matches())
            {
                // we support artifacts with different classifiers (e.g., activeio-core-3.1.0-tests.jar should not be in conflict with activeio-core-3.1.0.jar)
                String nameWithClassifier = matcher.group(BuildUtils.ARTIFACT_NAME_INDEX)
                if (matcher.group(BuildUtils.ARTIFACT_CLASSIFIER_INDEX) != null)
                    nameWithClassifier += matcher.group(BuildUtils.ARTIFACT_CLASSIFIER_INDEX)
                if (nameVersionMap.containsKey(nameWithClassifier))
                {
                    haveMultiples = true
                    conflictMessages += "Multiple existing ${matcher.group(BuildUtils.ARTIFACT_NAME_INDEX)} ${extension} files."
                }
                else if (matcher.group(BuildUtils.ARTIFACT_VERSION_INDEX) != null)
                {
                    this.logger.debug("adding name (with classifier): ${nameWithClassifier} and version: ${matcher.group(BuildUtils.ARTIFACT_VERSION_INDEX)}")
                    nameVersionMap.put(nameWithClassifier, new Tuple2(matcher.group(BuildUtils.ARTIFACT_VERSION_INDEX).substring(1), dFile))
                }
                else
                {
                    this.logger.debug("adding name (with classifier): ${nameWithClassifier} and no version")
                    nameVersionMap.put(nameWithClassifier, new Tuple2(null, dFile))
                }
            }
        }
        collection.files.each { File f ->
            Matcher matcher = BuildUtils.VERSIONED_ARTIFACT_NAME_PATTERN.matcher(f.name)
            if (matcher.matches())
            {
                String name = matcher.group(BuildUtils.ARTIFACT_NAME_INDEX)
                if (matcher.group(BuildUtils.ARTIFACT_CLASSIFIER_INDEX) != null)
                    name += matcher.group(BuildUtils.ARTIFACT_CLASSIFIER_INDEX)
                if (nameVersionMap.containsKey(name))
                {
                    String version = matcher.group(BuildUtils.ARTIFACT_VERSION_INDEX)
                    if (version != null)
                        version = version.substring(1)
                    this.logger.debug("Checking name (with classifier): ${name} and version ${version}")
                    String existingVersion = nameVersionMap.get(name).v1
                    if (existingVersion != version)
                    {
                        existingFilesInConflict.add(nameVersionMap.get(name).v2)
                        conflictMessages += "Conflicting version of ${matcher.group(BuildUtils.ARTIFACT_NAME_INDEX)} ${extension} file (${existingVersion} in directory vs. ${version} from build)."
                    }
                }
            }
        }

        if (!conflictMessages.isEmpty())
        {
            String message  = "Artifact versioning problem(s) in directory ${directory}:\n  " + conflictMessages.join("\n  ")
            ConflictAction action = getConflictAction()

            if (cleanTask != null && (action != ConflictAction.delete || haveMultiples))
                message += "\nRun the ${cleanTask} task to remove existing artifacts in that directory."
            // when there are multiple versions of some artifact, the user needs to decide which to keep and which to delete
            if (action == ConflictAction.delete && !haveMultiples)
            {
                this.logger.warn("INFO: " + message)
                this.logger.warn("INFO: Removing existing files that conflict with those from the build.")
                existingFilesInConflict.forEach({
                    File f ->
                        println("  Deleting ${f}")
                        f.delete()
                })
            }
            else if (action == ConflictAction.warn)
                this.logger.warn("WARNING: " + message)
            else
                throw new GradleException(message)
        }
    }
}
