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
package org.labkey.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.labkey.gradle.task.Bootstrap
import org.labkey.gradle.task.PickDb
import org.labkey.gradle.util.GroupNames

/**
 * Adds stand-alone tasks for setting database properties to be used by the server
 */
class Database implements Plugin<Project>
{
    @Override
    void apply(Project project)
    {
        addPickPgTask(project)
        addPickMSSQLTask(project)
        addBootstrapTask(project)
    }

    private static void addPickPgTask(Project project)
    {
        project.tasks.register("pickPg", PickDb) {
            PickDb task ->
                task.group = GroupNames.DATABASE
                task.description = "Switch to PostgreSQL configuration"
                task.dbType = "pg"
        }
    }

    private static void addPickMSSQLTask(Project project)
    {
        project.tasks.register("pickMSSQL", PickDb) {
            PickDb task ->
                task.group = GroupNames.DATABASE
                task.description = "Switch to SQL Server configuration"
                task.dbType = "mssql"
        }
    }

    private static void addBootstrapTask(Project project)
    {
        project.tasks.register("bootstrap", Bootstrap) {
            Bootstrap task ->
                task.group = GroupNames.DATABASE
                task.description = "Switch to bootstrap database properties as defined in current db.config file"
        }
    }
}


