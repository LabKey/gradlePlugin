/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.gradle.util

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.tasks.TaskProvider

import java.util.function.Consumer

class TaskUtils
{
    static void configureTaskIfPresent(Project project, String taskName, Closure closure)
    {
        doIfTaskPresent(project, taskName, task -> {
            task.configure closure
        })
    }

    static void addOptionalTaskDependency(Project project, Task task, String optionalTaskName)
    {
        getOptionalTask(project, optionalTaskName).ifPresent(optionalTask -> {
            task.dependsOn(optionalTask)
        })
    }

    static void doIfTaskPresent(Project project, String taskName, Consumer<TaskProvider> consumer)
    {
        getOptionalTask(project, taskName).ifPresent(task -> {
            consumer.accept(task)
        })
    }

    static Optional<TaskProvider> getOptionalTask(Project project, String taskName)
    {
        try {
            return Optional.of(project.tasks.named(taskName))
        }
        catch (UnknownTaskException ignore) {
            return Optional.empty()
        }
    }
}
