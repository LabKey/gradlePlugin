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

import org.apache.commons.lang3.SystemUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class DeployApp extends DefaultTask
{
    @InputDirectory
    File stagingModulesDir = new File((String) project.staging.modulesDir)

    @InputDirectory
    File stagingWebappDir = new File((String) project.staging.webappDir)

    private File _externalDir = new File((String) project.labkey.externalDir)

    @InputDirectory
    File stagingPipelineJarDir = new File((String) project.staging.pipelineLibDir)

    @OutputDirectory
    File deployModulesDir = new File((String) project.serverDeploy.modulesDir)

    @OutputDirectory
    File deployWebappDir = new File((String) project.serverDeploy.webappDir)

    @OutputDirectory
    File deployPipelineLibDir = new File((String) project.serverDeploy.pipelineLibDir)

    @OutputDirectory
    File deployBinDir = new File((String) project.serverDeploy.binDir)


    @TaskAction
    void action()
    {
        deployWebappDir()
        deployModules()
        deployPipelineJars()
        deployNlpEngine()
        deployPlatformBinaries()
    }


    private void deployWebappDir()
    {
        ant.copy(
                todir: deployWebappDir,
                preserveLastModified: true
        )
                {
                    fileset(dir: stagingWebappDir)
                }
    }

    private void deployModules()
    {
        ant.copy (
                todir: deployModulesDir,
                preserveLastModified: true,
        )
                {
                    fileset(dir: stagingModulesDir)
                }
    }

    private void deployPipelineJars()
    {
        project.copy( { CopySpec copy ->
            copy.from stagingPipelineJarDir
            copy.into deployPipelineLibDir
        })
    }

    private void deployPlatformBinaries()
    {
        deployBinDir.mkdirs()

        if (project.configurations.findByName("binaries") != null)
        {
            project.logger.info("Copying from binaries configuration to ${deployBinDir}")
            project.copy({
                CopySpec copy ->
                    copy.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
                    copy.from(project.configurations.binaries.collect { project.zipTree(it) })
                    copy.into deployBinDir.path
            })
            project.logger.info("Contents of ${deployBinDir}\n" + deployBinDir.listFiles());
        }
        // For TC builds, we deposit the artifacts of the Linux TPP Tools and Windows Proteomics Tools into
        // the external directory, so we want to copy those over as well.
        // TODO: package the output of these builds into the Artfactory artifact to simplify
        if (project.file(_externalDir).exists()) {
            project.logger.info("Copying from ${_externalDir} to ${project.serverDeploy.binDir}")
            if (SystemUtils.IS_OS_MAC)
                deployBinariesViaProjectCopy("osx")
            else if (SystemUtils.IS_OS_LINUX)
                deployBinariesViaProjectCopy("linux")
            else if (SystemUtils.IS_OS_WINDOWS)
                deployBinariesViaAntCopy("windows")
        }
    }

    // Use this method to preserve file permissions, since ant.copy does not, but this does not preserve last modified times
    private void deployBinariesViaProjectCopy(String osDirectory)
    {
        File parentDir = new File(_externalDir, "${osDirectory}")
        if (parentDir.exists())
        {
            List<File> subDirs = parentDir.listFiles new FileFilter() {
                @Override
                boolean accept(File pathname) {
                    return pathname.isDirectory()
                }
            }
            for (File dir : subDirs) {
                project.copy { CopySpec copy ->
                    copy.from dir
                    copy.into "${project.serverDeploy.binDir}"
                }
            }
        }
    }

    private void deployBinariesViaAntCopy(String osDirectory)
    {
        def fromDir = "${_externalDir}/${osDirectory}"
        if (project.file(fromDir).exists())
        {
            ant.copy(
                    todir: project.serverDeploy.binDir,
                    preserveLastModified: true
            )
                    {
                        ant.cutdirsmapper(dirs: 1)
                        fileset(dir: fromDir)
                                {
                                    exclude(name: "**.*")
                                }
                    }
        }
    }

    private void deployNlpEngine()
    {

        File nlpSource = new File(_externalDir, "nlp")
        if (nlpSource.exists())
        {
            File nlpDir = new File(deployBinDir, "nlp")
            nlpDir.mkdirs();
            ant.copy(
                    toDir: nlpDir,
                    preserveLastModified: true
            )
                    {
                        fileset(dir: nlpSource)
                                {
                                    exclude(name: "**/*.py?")
                                }
                    }
        }
    }
}
