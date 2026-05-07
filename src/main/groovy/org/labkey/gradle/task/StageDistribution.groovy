/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.RelativePath
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.labkey.gradle.plugin.ServerDeploy
import org.labkey.gradle.plugin.extension.DistributionExtension
import org.labkey.gradle.util.BuildUtils

import javax.inject.Inject

@DisableCachingByDefault(because="Outputs are in the staging directory")
abstract class StageDistribution extends DefaultTask
{
    @Inject abstract FileSystemOperations getFs()
    @Inject abstract ArchiveOperations getArchiveOps()

    @Input
    final abstract Property<String> distDir = project.objects.property(String).convention(project.hasProperty("distDir") ? (String) project.property("distDir") : "dist")

    @OutputDirectory
    final abstract DirectoryProperty modulesStagingDir = BuildUtils.getRootBuildDirectoryProperty(project, ServerDeploy.STAGING_MODULES_DIR)

    @OutputDirectory
    final abstract DirectoryProperty stagingDir = BuildUtils.getRootBuildDirectoryProperty(project, ServerDeploy.STAGING_DIR)

    @OutputDirectory
    final abstract DirectoryProperty pipelineJarStagingDir = BuildUtils.getRootBuildDirectoryProperty(project, ServerDeploy.STAGING_PIPELINE_DIR)

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    final abstract RegularFileProperty distributionFileProp = project.objects.fileProperty().fileValue(DistributionExtension.getDistributionFile(project, distDir.get()))

    @TaskAction
    void action()
    {
        File distributionFile = distributionFileProp.get().asFile
        String extension = DistributionExtension.TAR_ARCHIVE_EXTENSION
        FileTree distArchiveTree = archiveOps.tarTree(distributionFile)

        // first clean out the staging directory so we don't pick up modules not in this distribution
        fs.delete {
            it.delete(modulesStagingDir.get())
        }

        fs.copy({ CopySpec spec ->
            spec.from distArchiveTree.files
            spec.into modulesStagingDir.get()
            spec.include "**/*.module"
            spec.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        })

        String baseName = distributionFile.getName().substring(0, distributionFile.getName().length() - (extension.length() + 1))

        fs.copy({ CopySpec spec ->
            spec.from distArchiveTree
            spec.into stagingDir.get()
            spec.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
            spec.eachFile {
                FileCopyDetails fcp ->
                    if (fcp.relativePath.pathString.startsWith("${baseName}/labkeywebapp")) {
                        // remap the file to the root
                        String[] segments = fcp.relativePath.segments
                        for (int i = 0; i < segments.length - 1; i++) // HACK we should get rid of once we're not using ant to create distributions
                        {
                            segments[i] = segments[i].replace("labkeywebapp", "labkeyWebapp")
                        }
                        String[] pathSegments = segments[1..-1] as String[]
                        fcp.relativePath = new RelativePath(!fcp.file.isDirectory(), pathSegments)
                    } else {
                        fcp.exclude()
                    }
            }
            spec.includeEmptyDirs = false
        })

        fs.copy({ CopySpec spec ->
            spec.from distArchiveTree
            spec.into pipelineJarStagingDir.get()
            spec.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
            spec.eachFile {
                FileCopyDetails fcp ->
                    if (fcp.relativePath.pathString.startsWith("${baseName}/pipeline-lib")) {
                        // remap the file to the root
                        String[] segments = fcp.relativePath.segments
                        String[] pathSegments = segments[2..-1] as String[]
                        fcp.relativePath = new RelativePath(!fcp.file.isDirectory(), pathSegments)
                    } else {
                        fcp.exclude()
                    }
            }
            spec.includeEmptyDirs = false
        })
    }
}
