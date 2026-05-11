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
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.labkey.gradle.plugin.XsdDoc

import javax.inject.Inject

@CacheableTask
abstract class CreateJsDocs extends DefaultTask
{
    @Inject abstract ExecOperations getExec()

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    final abstract DirectoryProperty templateDir = project.objects.directoryProperty().convention(
            project.rootProject.layout.projectDirectory.dir("tools/jsdoc-toolkit/templates/jsdoc_substituted")
    )

    @Input
    final abstract Property<String> jarPath = project.objects.property(String).convention("${project.jsDoc.root}/jsrun.jar")

    @Input
    final abstract Property<String> jsPath = project.objects.property(String).convention("${project.jsDoc.root}/app/run.js")

    @OutputDirectory
    final abstract DirectoryProperty outputDir = project.objects.directoryProperty().convention(getJsDocDirectory(project).dir( "docs"))

    @Input
    abstract ListProperty<File> getFilesToProcess()

    static Directory getJsDocDirectory(Project project)
    {
        return XsdDoc.getClientDocsBuildDir(project).get().dir("javascript")
    }

    @TaskAction
    void createDocs()
    {
        exec.javaexec { exec ->
            exec.mainClass = "-jar"
            exec.args = [jarPath.get(),
                         jsPath.get(),
                         "--template=${templateDir.get()}",
                         "--directory=${outputDir.get()}",
                         "--verbose"]
            filesToProcess.get().each { File file ->
                exec.args += file.path
            }
        }
    }
}
