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
import org.gradle.api.GradleException
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.nio.file.Paths

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
        File distDir = new File(project.rootDir, "dist")
        if (project.hasProperty("distDir"))
        {
            if (Paths.get(project.property('distDir')).isAbsolute())
                distDir = new File(project.property('distDir'));
            else
                distDir = new File(project.rootDir, (String) project.property("distDir"))
        }
        if (!distDir.exists())
            throw new GradleException("Distribution directory ${distDir} not found")
        String extension = project.hasProperty("distType") ? project.property('distType') : "tar.gz"
        File[] distFiles = distDir.listFiles(new FilenameFilter() {

            @Override
            boolean accept(File dir, String name) {
                return name.endsWith(".${extension}");
            }
        })
        if (distFiles == null || distFiles.length == 0)
            throw new GradleException("No distribution found in directory ${distDir} with extension ${extension}")
        else if (distFiles.length > 1)
            throw new GradleException("${distDir} contains ${distFiles.length} files with extension ${extension}.  Only one is allowed.")
        distributionFile = distFiles[0]

        Boolean isTar = extension.equals("tar.gz")

        // first clean out the staging directory so we don't pick up modules not in this distribution
        project.delete modulesStagingDir

        project.copy({ CopySpec spec ->
            spec.from isTar ? project.tarTree(distributionFile).files : project.zipTree(distributionFile).files
            spec.into modulesStagingDir
            spec.include "**/*.module"
        })

        String baseName = distributionFile.getName().substring(0, distributionFile.getName().length()-(extension.length()+1))

        project.copy({ CopySpec spec ->
            spec.from isTar ? project.tarTree(distributionFile) : project.zipTree(distributionFile)
            spec.into stagingDir
            spec.eachFile {
                FileCopyDetails fcp ->
                if (fcp.relativePath.pathString.startsWith("${baseName}/labkeywebapp"))
                {
                    // remap the file to the root
                    String[] segments = fcp.relativePath.segments
                    for (int i = 0; i < segments.length - 1; i++) // HACK we should get rid of once we're not using ant to create distributions
                    {
                        segments[i] = segments[i].replace("labkeywebapp", "labkeyWebapp")
                    }
                    String[] pathSegments = segments[1..-1] as String[]
                    fcp.relativePath = new RelativePath(!fcp.file.isDirectory(), pathSegments)
                }
                else
                {
                    fcp.exclude()
                }
            }
            spec.includeEmptyDirs = false
        })

        project.copy({ CopySpec spec ->
            spec.from isTar ? project.tarTree(distributionFile) : project.zipTree(distributionFile)
            spec.into pipelineJarStagingDir
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
            spec.from isTar ? project.tarTree(distributionFile).files : project.zipTree(distributionFile).files
            spec.into tomcatJarStagingDir
            BuildUtils.getServerProject(project).configurations.tomcatJars.files.each {
                File file ->
                    spec.include file.getName()
            }
            // include these artifacts in case we are deploying from an ant-built distribution
            spec.include 'ant.jar'
            spec.include 'jtds.jar'
            spec.include 'mail.jar'
            spec.include 'mysql.jar'
            spec.include 'postgresql.jar'
            spec.include 'labkeyBootstrap.jar'
        })
    }
}
