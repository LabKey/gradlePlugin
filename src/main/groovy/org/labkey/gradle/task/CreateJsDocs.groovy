/*
 * Copyright (c) 2017 LabKey Corporation
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
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class CreateJsDocs extends DefaultTask
{
    @InputFiles
    List<File> getInputFiles()
    {
        List<File> files = []
        project.jsDoc.paths.each{ String path ->
            files += project.file(path)
        }
        return files
    }

    @OutputDirectory
    File getOutputDirectory()
    {
        return project.jsDoc.outputDir
    }

    @TaskAction
    void createDocs()
    {
        List<File> inputPaths = getInputFiles()
        project.javaexec { exec ->
            exec.mainClass = "-jar"
            exec.args = ["${project.jsDoc.root}/jsrun.jar",
                         "${project.jsDoc.root}/app/run.js",
                         "--template=${project.tasks.jsdocTemplate.destinationDir}",
                         "--directory=${getOutputDirectory()}",
                         "--verbose"]
            inputPaths.each { File file ->
                exec.args += file.path
            }
        }
    }
}
