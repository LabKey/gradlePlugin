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

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.FileTree

/**
 * This action can be applied to a task as a doLast action if we need to create gzipped versions of the output files
 * produced by a task.
 */
class GzipAction implements Action<Task>
{
    String[] extraExcludes = []

    @Override
    void execute(Task task)
    {
        FileTree tree = task.outputs.files.getAsFileTree().matching {
            include("**/*.css")
            include("**/*.js")
            include("**/*.html")
            exclude("WEB-INF/**")
            exclude("**/src/**")
            exclude(extraExcludes)
        }

        tree.each { File file ->
            task.project.ant.gzip(
                    src: file,
                    destfile: "${file.toString()}.gz"
            )
            task.project.logger.info("zipping file " + file + " to ${file.toString()}.gz")
        }
    }
}
