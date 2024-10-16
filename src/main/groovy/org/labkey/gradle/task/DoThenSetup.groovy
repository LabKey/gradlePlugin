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
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.extension.TeamCityExtension
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.DatabaseProperties
import org.labkey.gradle.util.PropertiesUtils

import java.util.function.Function

class DoThenSetup extends DefaultTask
{
    @Optional @Input
    protected DatabaseProperties databaseProperties
    @Input
    boolean dbPropertiesChanged = false

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
        doDatabaseTask()
        if (!embeddedConfigUpToDate()) {
            Properties configProperties = databaseProperties.getConfigProperties()
            configProperties.putAll(getExtraJdbcProperties())
            // in .properties files, backward slashes are seen as escape characters, so all paths must use forward slashes, even on Windows
            configProperties.setProperty("pathToServer", project.rootDir.getAbsolutePath().replaceAll("\\\\", "/"))

            configProperties.setProperty("serverPort", tcPropOrDefault(project,
                    TeamCityExtension::getLabKeyServerPort,
                    "serverPort",
                    project.hasProperty("useSsl") ? "8443" : "8080"))

            configProperties.setProperty("shutdownPort", tcPropOrDefault(project,
                    TeamCityExtension::getLabKeyServerShutdownPort,
                    "shutdownPort",
                    "8081"))

            if (project.hasProperty("useSsl")) {
                configProperties.setProperty("keyStore", tcPropOrDefault(project,
                        TeamCityExtension::getLabKeyServerKeystore,
                        "keyStore",
                        "/opt/teamcity-agent/localhost.keystore"))

                configProperties.setProperty("keyStorePassword", tcPropOrDefault(project,
                        TeamCityExtension::getLabKeyServerKeystorePassword,
                        "keyStorePassword",
                        "changeit"))
            }

            String embeddedDir = BuildUtils.getEmbeddedConfigPath(project)
            File configsDir = new File(BuildUtils.getConfigsProject(project).projectDir, "configs")
            project.copy({ CopySpec copy ->
                copy.from configsDir
                copy.into embeddedDir
                copy.include "application.properties"
                copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
                copy.filter({ String line ->
                    // Always uncomment properties prepended by '#setupTask#'
                    line = line.replace("#setupTask#", "")
                    if (project.hasProperty("useSsl")) {
                        line = line.replace("#server.ssl", "server.ssl")
                    }
                    if (project.hasProperty("useLocalBuild") && "false" != project.property("useLocalBuild")) {
                        // Enable properties that require 'useLocalBuild' (e.g. 'context.webAppLocation' and 'spring.devtools.restart.additional-paths')
                        line = line.replace("#useLocalBuild#", "")
                    }
                    else {
                        // Remove placeholder
                        line = line.replace("#useLocalBuild#", "#")
                    }
                    if (configProperties.containsKey("extraJdbcDataSource") && line.contains("=@@extraJdbc"))
                    {
                        line = line.replace("#context.", "context.")
                    }
                    if (line.startsWith("#")) {
                        return line // Don't apply replacements to comments
                    }
                    return PropertiesUtils.replaceProps(line, configProperties, false)
                })
            })
            BuildUtils.updateRestartTriggerFile(project)
        }
    }

    /**
     * Get 'extraJdbc*' properties from TeamCity.
     * Used as string replacements when deploying 'labkey.xml' and 'application.properties'
     */
    private Properties getExtraJdbcProperties()
    {
        def extraJdbcProperties = new Properties()
        def tcProperties = TeamCityExtension.getTeamCityProperties(project)
        for (Map.Entry entry : tcProperties.entrySet())
        {
            if (entry.getKey().startsWith("extraJdbc"))
            {
                extraJdbcProperties.put(entry.getKey(), entry.getValue())
            }
        }
        return extraJdbcProperties
    }

    boolean embeddedConfigUpToDate()
    {
        if (this.dbPropertiesChanged)
            return false

        File dbPropFile = DatabaseProperties.getPickedConfigFile(project)
        File applicationPropsFile = new File(BuildUtils.getEmbeddedConfigPath(project), "application.properties")
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

    private static String tcPropOrDefault(Project project, Function<Project, String> tcPropertyFunc, String projectPropertyName, String defaultValue)
    {
        String value = tcPropertyFunc.apply(project)
        if (value == null) {
            if (project.hasProperty(projectPropertyName))
                value = (String) project.property(projectPropertyName)
            else
                value = defaultValue
        }
        return value
    }
}
