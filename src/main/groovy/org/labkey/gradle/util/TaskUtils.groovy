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
import java.util.function.Function

class TaskUtils
{
    static void configureTaskIfPresent(Project project, String taskName, Closure closure)
    {
        try {
            project.tasks.named(taskName).configure closure
        }
        catch (UnknownTaskException ignore) {}
    }

    static void addOptionalTaskDependency(Project project, Task task, String optionalTaskName)
    {
        try {
            def optionalTask = project.tasks.named(optionalTaskName)
            task.dependsOn(optionalTask)
        } catch (UnknownTaskException ignore) { }
    }

    static TaskProvider doIfTaskPresent(Project project, String taskName, Consumer<TaskProvider> consumer)
    {
        return getFromTaskIfPresent(project, taskName, task -> {
            consumer.accept(task)
            return task
        })
    }

    static <R> R getFromTaskIfPresent(Project project, String taskName, Function<TaskProvider, R> func)
    {
        return getFromTaskIfPresent(project, taskName, func, null)
    }

    static <R> R getFromTaskIfPresent(Project project, String taskName, Function<TaskProvider, R> func, R defaultValue)
    {
        try {
            var task = project.tasks.named(taskName)
            return func.apply(task)
        }
        catch (UnknownTaskException ignore) {
            return defaultValue
        }
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
