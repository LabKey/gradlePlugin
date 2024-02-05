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
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.FileTree
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.extension.DistributionExtension

class StageDistribution extends DefaultTask
{
    protected File distributionFile = null

    @OutputDirectory
    File modulesStagingDir = new File((String) project.staging.modulesDir)

    @OutputDirectory
    File stagingDir = new File((String) project.staging.dir)

    @OutputDirectory
    File pipelineJarStagingDir = new File((String) project.staging.pipelineLibDir)

    @OutputDirectory
    File tomcatJarStagingDir = new File((String) project.staging.tomcatLibDir)


    @TaskAction
    void action()
    {
        distributionFile = DistributionExtension.getDistributionFile(project)
        String extension = distributionFile.getName().endsWith(DistributionExtension.ZIP_ARCHIVE_EXTENSION) ? DistributionExtension.ZIP_ARCHIVE_EXTENSION : DistributionExtension.TAR_ARCHIVE_EXTENSION
        Boolean isTar = extension.equals(DistributionExtension.TAR_ARCHIVE_EXTENSION)
        FileTree distArchiveTree = isTar ? project.tarTree(distributionFile) : project.zipTree(distributionFile)

        // first clean out the staging directory so we don't pick up modules not in this distribution
        project.delete modulesStagingDir

        project.copy({ CopySpec spec ->
            spec.from distArchiveTree.files
            spec.into modulesStagingDir
            spec.include "**/*.module"
            spec.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        })

        String baseName = distributionFile.getName().substring(0, distributionFile.getName().length() - (extension.length() + 1))

        project.copy({ CopySpec spec ->
            spec.from distArchiveTree
            spec.into stagingDir
            spec.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
            spec.eachFile {
                FileCopyDetails fcp ->
                    if (!fcp.relativePath.pathString.startsWith("${baseName}/labkeywebapp")) {
                        fcp.exclude()
                    }
            }
            spec.includeEmptyDirs = false
        })

        project.copy({ CopySpec spec ->
            spec.from distArchiveTree
            spec.into pipelineJarStagingDir
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

        project.copy({ CopySpec spec ->
            spec.from distArchiveTree.matching {include '*/tomcat-lib/*.jar'}.files
            spec.into tomcatJarStagingDir
            spec.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
        })
    }
}
