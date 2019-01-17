/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
package org.labkey.gradle.plugin.extension

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.DatabaseProperties
import org.labkey.gradle.util.PropertiesUtils

/**
 * Created by susanh on 4/23/17.
 */
class UiTestExtension
{
    String propertiesFile = "test.properties"

    private Properties config = null
    private Project project
    private TeamCityExtension tcExtension

    UiTestExtension(Project project)
    {
        this.project = project
        TeamCityExtension tcExtension = project.extensions.findByType(TeamCityExtension.class)
        if (TeamCityExtension.isOnTeamCity(project) && tcExtension == null)
            this.tcExtension = project.extensions.create("teamCity", TeamCityExtension, project)
    }

    private void setConfig()
    {
        this.config = new Properties()
        this.config.setProperty("debugSuspendSelenium", "n")

        // Issue 32153: When running tests and the pickDb tasks within the same command if the config.properties file
        // does not exist (or exists and is a different database than intended), the set of tests run will not be
        // properly filtered.  For running on command line, the easiest solution is to simply run the pickDB task as
        // a separate task.  This is more cumbersome on TeamCity, but we already have properties that specify the
        // database type there, so we'll use those.
        if (this.tcExtension != null)
        {
            project.logger.info("Getting database properties from TC configuration")
            List<DatabaseProperties> dbProperties = tcExtension.getDatabaseTypes()
            if (dbProperties.isEmpty())
                throw new GradleException("TeamCity configuration problem(s): No database properties defined.")
            else if (dbProperties.size() > 1)
                throw new GradleException("TeamCity configuration problem(s): More than one database type defined. Cannot determine which to use for configuring tests.")
            else
            {
                project.logger.info("got databaseType ${dbProperties.get(0).getShortType()}")
                this.config.put("databaseType", dbProperties.get(0).getShortType())
            }
        }
        else if (project.hasProperty("databaseType"))
        {
            this.config.put("databaseType", project.property("databaseType"))
        }
        else if (DatabaseProperties.getPickedConfigFile(project).exists())
        {
            project.logger.info("Found config file ${DatabaseProperties.getPickedConfigFile(project).getAbsolutePath()} to get db properties from")
            DatabaseProperties dbProperties = new DatabaseProperties(project, false)
            // read database configuration, but don't include jdbcUrl and other non-"database"
            // properties because they "cause problems" (quote from the test/build.xml file)
            for (String name : dbProperties.getConfigProperties().stringPropertyNames())
            {
                if (name.contains("database"))
                    this.config.put(name, dbProperties.getConfigProperties().getProperty(name))
            }
            project.logger.info("Got databaseType ${this.config.get("databaseType")} from config file.")
        }

        if (project.findProject(BuildUtils.getTestProjectPath(project.gradle)) != null)
        // read test.properties file
            PropertiesUtils.readProperties(project.project(BuildUtils.getTestProjectPath(project.gradle)).file(propertiesFile), this.config)
        // if the test.properties file is not available, all properties will need to be provided via project properties
        for (String name : config.propertyNames())
        {
            // two of the test.property names ('test' and 'clean') are the same as the
            // names of default tasks that come with the Java plugin.  All tasks are also
            // properties of a project, so we test for a String type (passed through the
            // command line) and override the property in the file only if we have a new
            // String.
            if (project.hasProperty(name) && project.property(name) instanceof String)
            {
                config.setProperty(name, project.property(name).toString())
            }
        }
    }

    Properties getConfig()
    {
        if (this.config == null)
            setConfig()
        return this.config;
    }

    Object getTestConfig(String name)
    {
        if (project.hasProperty(name))
            return project.property(name)
        else
        {
            return getConfig().get(name)
        }
    }

}
