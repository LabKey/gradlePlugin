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
package org.labkey.gradle.plugin.extension

import org.gradle.api.Project
import org.labkey.gradle.util.DatabaseProperties

class TeamCityExtension
{
    String databaseName
    Boolean dropDatabase = false
    List<DatabaseProperties> databaseTypes = new ArrayList<>()
    List<String> validationMessages = new ArrayList<>()
    Project project

    // TODO remove this?  TeamCity seems to be happy with postgres10 and sqlserver2017 without updates here
    private static final Map<String, DatabaseProperties> SUPPORTED_DATABASES = new HashMap<>()
    static
    {
        SUPPORTED_DATABASES.put("postgres9.2", new DatabaseProperties("postgres9.2", "pg", "9.2"))
        SUPPORTED_DATABASES.put("postgres9.3", new DatabaseProperties("postgres9.3", "pg", "9.3"))
        SUPPORTED_DATABASES.put("postgres9.4", new DatabaseProperties("postgres9.4", "pg", "9.4"))
        SUPPORTED_DATABASES.put("postgres9.5", new DatabaseProperties("postgres9.5", "pg", "9.5"))
        SUPPORTED_DATABASES.put("postgres9.6", new DatabaseProperties("postgres9.6", "pg", "9.6"))
        SUPPORTED_DATABASES.put("sqlserver2012", new DatabaseProperties("sqlserver2012", "mssql", "2012"))
        SUPPORTED_DATABASES.put("sqlserver2014", new DatabaseProperties("sqlserver2014", "mssql", "2014"))
        SUPPORTED_DATABASES.put("sqlserver2016", new DatabaseProperties("sqlserver2016", "mssql", "2016"))
    }

    TeamCityExtension(Project project)
    {
        this.project = project
        setDatabaseProperties()
        setValidationMessages()
    }

    Boolean isValidForTestRun()
    {
        return validationMessages.isEmpty()
    }

    void setValidationMessages()
    {
        if (getTeamCityProperty("suite").isEmpty())
            validationMessages.add("'suite' property not specified")
        if (getTeamCityProperty("tomcat.home").isEmpty())
            validationMessages.add("'tomcat.home' property not specified")
        if (getTeamCityProperty("tomcat.port").isEmpty())
            validationMessages.add("'tomcat.port' property not specified")
        if (this.databaseTypes.isEmpty())
            validationMessages.add("'database.types' property not specified or does not specify a supported database.")
        if (getTeamCityProperty('agent.name').isEmpty())
            validationMessages.add("'agent.name' property not specified")
        if (getTeamCityProperty('teamcity.projectName').isEmpty())
            validationMessages.add("'teamcity.projectName' property not specified")
        if (getTeamCityProperty('tomcat.debug').isEmpty())
            validationMessages.add("'tomcat.debug' property (for debug port) not specified")
    }

    private void setDatabaseProperties()
    {
        if ((Boolean) getTeamCityProperty("build.is.personal", false))
        {
            this.databaseName = "LabKey_PersonalBuild"
            this.dropDatabase = true
        }
        else
        {
            String name = getTeamCityProperty("teamcity.buildType.id")
            if (!(Boolean) getTeamCityProperty("teamcity.build.branch.is_default", true))
                name = "${getTeamCityProperty('teamcity.build.branch')}_${name}"
            this.databaseName = name.replaceAll("[/\\.\\s-]", "_")
            String dropProperty = getTeamCityProperty('drop.database')
            this.dropDatabase = dropProperty.equals("1") || dropProperty.equalsIgnoreCase("true")
        }
        String type = getTeamCityProperty("database.types")
        DatabaseProperties props
        if (SUPPORTED_DATABASES.containsKey(type))
        {
            props = SUPPORTED_DATABASES.get(type)
        }
        else
        {
            String typeName = getTeamCityProperty("database.${type}.type")
            if (typeName.isEmpty())
            {
                validationMessages.add("database.${type}.type not specified. Needed to customize database props")
            }
            props = new DatabaseProperties(typeName, typeName, null)
        }
        props.setProject(project)
        props.jdbcDatabase = getDatabaseName()
        if (!getTeamCityProperty("database.${type}.jdbcURL").isEmpty())
        {
            props.setJdbcURL(getTeamCityProperty("database.${type}.jdbcURL"))
            if (getTeamCityProperty("database.${type}.port").isEmpty() && this.dropDatabase)
                validationMessages.add("'database.${type}.port' not specified. Unable to drop database.")
        }
        else if (getTeamCityProperty("database.${type}.port").isEmpty())
            validationMessages.add("database.${type}.jdbcURL and database.${type}.port not specified. Connection not possible.")

        if (!getTeamCityProperty("database.${type}.port").isEmpty())
            props.setJdbcPort(getTeamCityProperty("database.${type}.port"))

        if (!getTeamCityProperty("database.${type}.host").isEmpty())
            props.setJdbcHost(getTeamCityProperty("database.${type}.host"))

        if (!getTeamCityProperty("database.${type}.user").isEmpty())
            props.setJdbcUser(getTeamCityProperty("database.${type}.user"))

        if (!getTeamCityProperty("database.${type}.password").isEmpty())
            props.setJdbcPassword(getTeamCityProperty("database.${type}.password"))

        this.databaseTypes.add(props)
    }

    static boolean isOnTeamCity(Project project)
    {
        return project.hasProperty('teamcity')
    }

    String getTeamCityProperty(String name)
    {
        return getTeamCityProperty(name, "")
    }

    Object getTeamCityProperty(String name, Object defaultValue)
    {
        getTeamCityProperty(project, name, defaultValue)
    }

    static Object getTeamCityProperty(Project project, String name, Object defaultValue)
    {
        if (isOnTeamCity(project))
            return project.teamcity[name] != null ? project.teamcity[name] : defaultValue
        else if (project.hasProperty(name))
            return project.property(name)
        else
            return defaultValue
    }
}
