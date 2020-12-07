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
package org.labkey.gradle.util

import org.gradle.api.Project

class DatabaseProperties
{
    private static final String PICKED_DATABASE_CONFIG_FILE = "config.properties"

    private static final String JDBC_URL_PROP = "jdbcURL"
    private static final String JDBC_PORT_PROP = "jdbcPort"
    private static final String JDBC_DATABASE_PROP = "jdbcDatabase"
    private static final String JDBC_HOST_PROP = "jdbcHost"
    private static final String JDBC_URL_PARAMS_PROP = "jdbcURLParameters"
    private static final String JDBC_USER_PROP = "jdbcUser"
    private static final String JDBC_PASSWORD_PROP = "jdbcPassword"
    private static final String BOOTSTRAP_DB_PROP = "databaseBootstrap"
    private static final String DEFAULT_DB_PROP = "databaseDefault"
    private static final String DEFAULT_HOST_PROP = "databaseDefaultHost"
    private static final String DEFAULT_PORT_PROP = "databaseDefaultPort"

    String dbTypeAndVersion // e.g., postgres9.2
    String shortType // pg or mssql
    String version // database version, e.g. 9.2

    Properties configProperties
    transient private Project _project

    DatabaseProperties(String dbTypeAndVersion, String shortType, version)
    {
        this.dbTypeAndVersion = dbTypeAndVersion
        this.shortType = shortType
        this.version = version
        this.configProperties = new Properties()
    }

    DatabaseProperties(Project project, Boolean useBootstrap)
    {
        this._project = project
        this.configProperties = readDatabaseProperties(project)
        if (!this.configProperties.isEmpty())
        {
            setDefaultJdbcProperties(useBootstrap)
            if (!useBootstrap)
                interpolateCompositeProperties()
        }
    }

    DatabaseProperties(Project project, DatabaseProperties copyProperties)
    {
        this._project = project
        this.configProperties = (Properties) copyProperties.configProperties.clone()
        this.dbTypeAndVersion = copyProperties.dbTypeAndVersion
        this.shortType = copyProperties.shortType
        this.version = copyProperties.version
    }

    static File getPickedConfigFile(Project project)
    {
        return getConfigFile(project, PICKED_DATABASE_CONFIG_FILE)
    }

    private static File getConfigFile(Project project, String dbConfigFile)
    {
        return BuildUtils.getConfigsProject(project).file(dbConfigFile)
    }

    void setProject(Project project)
    {
        this._project = project
    }

    void setJdbcURL(String jdbcURL)
    {
        this.configProperties.setProperty(JDBC_URL_PROP, jdbcURL)
    }

    String getJdbcURL()
    {
        return this.configProperties.get(JDBC_URL_PROP)
    }

    void setJdbcDatabase(String database)
    {
        this.configProperties.setProperty(JDBC_DATABASE_PROP, database)
    }

    String getJdbcDatabase()
    {
        return this.configProperties.get(JDBC_DATABASE_PROP)
    }

    void setJdbcPort(String port)
    {
        this.configProperties.setProperty(JDBC_PORT_PROP, port)
    }

    String getJdbcPort()
    {
        return this.configProperties.get(JDBC_PORT_PROP)
    }

    void setJdbcHost(String host)
    {
        this.configProperties.setProperty(JDBC_HOST_PROP, host)
    }

    String getJdbcHost()
    {
        return this.configProperties.get(JDBC_HOST_PROP)
    }

    void setJdbcUser(String user)
    {
        this.configProperties.setProperty(JDBC_USER_PROP, user)
    }

    String getJdbcUser()
    {
        return this.configProperties.get(JDBC_USER_PROP)
    }

    void setJdbcPassword(String password)
    {
        this.configProperties.setProperty(JDBC_PASSWORD_PROP, password)
    }

    String getJdbcPassword()
    {
        return this.configProperties.get(JDBC_PASSWORD_PROP)
    }

    void setJdbcUrlParams(String urlParams)
    {
        this.configProperties.setProperty(JDBC_URL_PARAMS_PROP, urlParams)
    }

    String getJdbcUrlParams()
    {
        return this.configProperties.get(JDBC_URL_PARAMS_PROP)
    }

    void setDefaultJdbcProperties(Boolean bootstrap)
    {
        if (getJdbcDatabase() == null)
        {
            if (bootstrap)
                setJdbcDatabase(getConfigProperty(BOOTSTRAP_DB_PROP))
            else
                setJdbcDatabase(getConfigProperty(DEFAULT_DB_PROP))
        }
        if (getJdbcHost() == null)
            setJdbcHost(getConfigProperty(DEFAULT_HOST_PROP))
        if (getJdbcPort() == null)
            setJdbcPort(getConfigProperty(DEFAULT_PORT_PROP))
        if (getJdbcUrlParams() == null)
            setJdbcUrlParams("")
    }

    private String getConfigProperty(String property, defaultValue="")
    {
        if (this.configProperties.get(property) != null)
            return (String) this.configProperties.get(property)
        else
        {
            _project.logger.info("Default database config property ${property} not defined; returning '${defaultValue}'.")
            return defaultValue
        }
    }

    void interpolateCompositeProperties()
    {
        this.configProperties.setProperty(JDBC_URL_PROP, PropertiesUtils.parseCompositeProp(_project, this.configProperties, this.configProperties.getProperty(JDBC_URL_PROP)))
    }

    void mergePropertiesFromFile()
    {
        Properties fileProperties = readDatabaseProperties(_project)
        for (String name : fileProperties.propertyNames())
        {
            if (this.configProperties.getProperty(name) == null)
            {
                this.configProperties.setProperty(name, fileProperties.getProperty(name))
            }
        }
        setDefaultJdbcProperties(false)
    }

    void writeDbProps()
    {
        writeDatabaseProperty(_project, JDBC_URL_PROP, PropertiesUtils.parseCompositeProp(_project, this.configProperties, this.configProperties.getProperty(JDBC_URL_PROP)))
        writeDatabaseProperty(_project, JDBC_USER_PROP, getJdbcUser())
        writeDatabaseProperty(_project, JDBC_PASSWORD_PROP, getJdbcPassword())
    }

    static Properties readDatabaseProperties(Project project)
    {
        return _readDatabaseProperties(project, PICKED_DATABASE_CONFIG_FILE)
    }

    private static Properties _readDatabaseProperties(Project project, String configFile)
    {
        if (getConfigFile(project, configFile).exists())
        {
            Properties props = PropertiesUtils.readFileProperties(BuildUtils.getConfigsProject(project), configFile)
            return props
        }
        else
        {
            project.logger.info("No file ${configFile} found.  Returning empty properties.")
            return new Properties()
        }
    }

    private void writeDatabaseProperty(Project project, String name, String value)
    {
        project.ant.propertyfile(
                file: getPickedConfigFile(project)
        )
                {
                    entry( key: name, value: value)
                }
    }
}
