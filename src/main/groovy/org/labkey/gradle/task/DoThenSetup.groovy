/*
 * Copyright (c) 2016-2018 LabKey Corporation
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
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.ServerDeploy
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.DatabaseProperties
import org.labkey.gradle.util.PropertiesUtils


class DoThenSetup extends DefaultTask
{
    @Optional @Input
    protected DatabaseProperties databaseProperties
    @Input
    boolean dbPropertiesChanged = false

    DoThenSetup()
    {
        Project serverProject = BuildUtils.getServerProject(project)
        if (serverProject != null)
            this.dependsOn serverProject.configurations.tomcatJars
    }

    private static boolean canCreate(File file)
    {
        file = file.getParentFile()

        while (file != null)
        {
            if (file.exists())
            {
                return file.canWrite() && file.canRead()
            }
            file = file.getParentFile()
        }
        return false
    }

    protected void doDatabaseTask()
    {
        setDatabaseProperties()
    }

    @TaskAction
    void setup() {
        project.tomcat.validateCatalinaHome()
        File tomcatConfDir = project.file(project.tomcat.tomcatConfDir)
        if (tomcatConfDir.exists())
        {
            if (!tomcatConfDir.isDirectory())
                throw new GradleException("No such directory: ${tomcatConfDir.absolutePath}")
            if (!tomcatConfDir.canWrite() || !tomcatConfDir.canRead())
                throw new GradleException("Directory ${tomcatConfDir.absolutePath} does not have proper permissions")
        }
        else if (!canCreate(tomcatConfDir))
            throw new GradleException("Insufficient permissions to create ${tomcatConfDir.absolutePath}")

        doDatabaseTask()

        if (!embeddedConfigUpToDate())
        {
            Properties configProperties = databaseProperties.getConfigProperties()
            String embeddedDir = BuildUtils.getEmbeddedConfigPath(project);
            File configsDir = new File(BuildUtils.getConfigsProject(project).projectDir, "configs")
            project.copy({CopySpec copy ->
                copy.from configsDir
                copy.into embeddedDir
                copy.include "application.properties"
                copy.filter({String line ->
                    return PropertiesUtils.replaceProps(line, configProperties, false)
                })
            })

        }

    }

    boolean embeddedConfigUpToDate()
    {
        if (this.dbPropertiesChanged)
            return false

        File dbPropFile = DatabaseProperties.getPickedConfigFile(project)
        File applicationPropsFile = new File("${project.rootProject.projectDir}/server/embedded/config", "application.properties")
        if (!dbPropFile.exists() || !applicationPropsFile.exists())
            return false
        if (dbPropFile.lastModified() < applicationPropsFile.lastModified())
        {
            return true
        }
        return false
    }

    protected void setDatabaseProperties()
    {
        databaseProperties = new DatabaseProperties(project, false)
    }

    void setDatabaseProperties(DatabaseProperties dbProperties)
    {
        this.databaseProperties = dbProperties
    }

    DatabaseProperties getDatabaseProperties()
    {
        return databaseProperties
    }
}
