/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

@CacheableTask
abstract class CopyJsp extends DefaultTask
{
    public static final String WEBAPP_DIR = "jspWebappDir/webapp"

    @Inject abstract FileSystemOperations getFs()

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    final abstract DirectoryProperty srcDir = project.objects.directoryProperty().convention(project.layout.projectDirectory.dir('src'))

    @OutputDirectory
    final abstract DirectoryProperty webappDir = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir(WEBAPP_DIR).get())

    @TaskAction
    void action() {
        fs.delete {
            it.delete(webappDir.get().dir("org"))
        }
        fs.copy {
            from srcDir.get()
            into webappDir.get()
            include '**/*.jsp'
        }
    }
}
