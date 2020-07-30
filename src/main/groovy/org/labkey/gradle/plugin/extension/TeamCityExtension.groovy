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

import org.gradle.api.Project
import org.labkey.gradle.util.DatabaseProperties

class TeamCityExtension
{
    String databaseName
    Boolean dropDatabase = false
    List<DatabaseProperties> databaseTypes = new ArrayList<>()
    List<String> validationMessages = new ArrayList<>()
    Project project

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
        String typeAndVersion = getTeamCityProperty("database.types") // despite the naming here, there is only one type specified
        String typeName = getTeamCityProperty("database.${typeAndVersion}.type")
        if (typeName.isEmpty())
        {
            validationMessages.add("database.${typeAndVersion}.type not specified. Needed to customize database props")
        }
        DatabaseProperties props = new DatabaseProperties(typeAndVersion, typeName, null)

        props.setProject(project)
        props.jdbcDatabase = getDatabaseName()
        if (!getTeamCityProperty("database.${typeAndVersion}.jdbcURL").isEmpty())
        {
            props.setJdbcURL(getTeamCityProperty("database.${typeAndVersion}.jdbcURL"))
            if (getTeamCityProperty("database.${typeAndVersion}.port").isEmpty() && this.dropDatabase)
                validationMessages.add("'database.${typeAndVersion}.port' not specified. Unable to drop database.")
        }
        else if (getTeamCityProperty("database.${typeAndVersion}.port").isEmpty())
            validationMessages.add("database.${typeAndVersion}.jdbcURL and database.${typeAndVersion}.port not specified. Connection not possible.")

        if (!getTeamCityProperty("database.${typeAndVersion}.port").isEmpty())
            props.setJdbcPort(getTeamCityProperty("database.${typeAndVersion}.port"))

        if (!getTeamCityProperty("database.${typeAndVersion}.host").isEmpty())
            props.setJdbcHost(getTeamCityProperty("database.${typeAndVersion}.host"))

        if (!getTeamCityProperty("database.${typeAndVersion}.user").isEmpty())
            props.setJdbcUser(getTeamCityProperty("database.${typeAndVersion}.user"))

        if (!getTeamCityProperty("database.${typeAndVersion}.password").isEmpty())
            props.setJdbcPassword(getTeamCityProperty("database.${typeAndVersion}.password"))

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

    static String getLabKeyServer(Project project)
    {
        return getTeamCityProperty(project, "labkey.server", "http://localhost")
    }

    static String getLabKeyUsername(Project project)
    {
        return getTeamCityProperty(project, "labkey.server.email", "teamcity@labkey.test")
    }

    static String getLabKeyPassword(Project project)
    {
        return getTeamCityProperty(project, "labkey.server.password", "yekbal1!")
    }
}
