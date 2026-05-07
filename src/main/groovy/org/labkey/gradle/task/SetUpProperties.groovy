/*
 * Copyright (c) 2016-2025 LabKey Corporation
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

import groovy.sql.Sql
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.UntrackedTask
import org.gradle.work.DisableCachingByDefault
import org.labkey.gradle.plugin.extension.TeamCityExtension
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.DatabaseProperties
import org.labkey.gradle.util.PropertiesUtils

import javax.inject.Inject

@DisableCachingByDefault(because="Outputs are not in the build directory")
abstract class SetUpProperties extends TeamCityPropertiesTask
{
    @Internal
    private DatabaseProperties databaseProperties

    @InputFiles @Classpath
    abstract ConfigurableFileCollection getDriverFiles()

    @Input
    boolean dbPropertiesChanged = false

    @OutputFile
    final abstract RegularFileProperty chosenPropsFile =  project.objects.fileProperty().fileValue(DatabaseProperties.getPickedConfigFile(project))

    @OutputFile
    final abstract RegularFileProperty applicationPropsFile = project.objects.fileProperty().fileValue(new File(BuildUtils.getEmbeddedConfigPath(project), "application.properties"))

    @Input
    final abstract Property<Boolean> useSsl = project.objects.property(Boolean).convention(project.hasProperty("useSsl"))
    @Input
    final abstract Property<String> portNumber = project.objects.property(String).convention(project.hasProperty("useSsl") ? "8443" : "8080")
    @Input
    final abstract Property<Boolean> useLocalBuild = project.objects.property(Boolean).convention(project.hasProperty("useLocalBuild") && "false" != project.property("useLocalBuild"))

    @Input // in .properties files, backward slashes are seen as escape characters, so all paths must use forward slashes, even on Windows
    final abstract Property<String> pathToServer = project.objects.property(String).convention(project.rootDir.getAbsolutePath().replaceAll("\\\\", "/"))

    @Input
    final abstract Property<String> embeddedConfigDir = project.objects.property(String).convention(BuildUtils.getEmbeddedConfigPath(project))

    @InputDirectory @PathSensitive(PathSensitivity.ABSOLUTE)
    File configsDir = new File(BuildUtils.getConfigsProject(project).projectDir, "configs")

    @OutputFile
    final abstract RegularFileProperty restartTriggerFile = project.objects.fileProperty().fileValue(BuildUtils.getRestartTriggerFile(project))

    @Inject abstract FileSystemOperations getFs()

    void setUpProperties() {
        if (!embeddedConfigUpToDate()) {
            Properties configProperties = databaseProperties.getConfigProperties()
            configProperties.putAll(getExtraJdbcProperties())
            configProperties.setProperty("pathToServer", pathToServer.get())
            configProperties.setProperty("serverPort", labKeyServerPort.get())
            configProperties.setProperty("contextPath", contextPath.get())
            configProperties.setProperty("shutdownPort", shutdownPort.get())
            if (useSsl.get()) {
                configProperties.setProperty("keyStore", keyStore.get())
                configProperties.setProperty("keyStorePassword", keyStorePassword.get())
            }

            fs.copy({ CopySpec copy ->
                copy.from configsDir
                copy.into embeddedConfigDir.get()
                copy.include "application.properties"
                copy.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
                copy.filter({ String line ->
                    // Always uncomment properties prepended by '#setupTask#'
                    line = line.replace("#setupTask#", "")
                    if (useSsl.get()) {
                        line = line.replace("#server.ssl", "server.ssl")
                    }
                    if (useLocalBuild.get()) {
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
                    if (configProperties.containsKey("contextPath") && line.contains("=@@contextPath"))
                    {
                        line = line.replace("#context.", "context.")
                    }
                    if (line.startsWith("#")) {
                        return line // Don't apply replacements to comments
                    }
                    return PropertiesUtils.replaceProps(line, configProperties)
                })
            })
            BuildUtils.updateRestartTriggerFile(useLocalBuild.get(), restartTriggerFile.get().asFile)
        }
    }

    /**
     * Get 'extraJdbc*' properties from TeamCity.
     * Used as string replacements when deploying 'application.properties'
     */
    @Input
    Properties getExtraJdbcProperties()
    {
        def extraJdbcProperties = new Properties()
        def tcProperties = isOnTeamCity.get() ? TeamCityExtension.getTeamCityProperties(project) : new Properties()
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

        File dbPropFile = chosenPropsFile.get().getAsFile()
        File _applicationPropsFile = applicationPropsFile.get().getAsFile()
        if (!dbPropFile.exists() || !_applicationPropsFile.exists())
            return false
        if (dbPropFile.lastModified() < _applicationPropsFile.lastModified())
        {
            return true
        }
        return false
    }

    protected void setDatabaseProperties()
    {
        databaseProperties = new DatabaseProperties(getPath(), chosenPropsFile.get().asFile, false)
    }

    void setDatabaseProperties(DatabaseProperties dbProperties)
    {
        this.databaseProperties = dbProperties
    }

    DatabaseProperties getDatabaseProperties()
    {
        return databaseProperties
    }

    void execSql(DatabaseProperties params, String sql)
    {
        params.interpolateCompositeProperties()
        String url = params.getJdbcURL()
        String user = params.getJdbcUser()
        String password = params.getJdbcPassword()
        String driverClassName = params.getJdbcDriverClassName()
        logger.info("in execSql: url ${url} driverClassName ${driverClassName}")
        logger.debug(" user ${user} password ${password}")

        var sqlLoader = Sql.classLoader
        // N.B. It seems like this modification of the loader classpath should not be necessary (or possible) and we
        // should be able to declare the dependencies on the driver jars in the buildscript { dependencies { } } block,
        // but see this (admittedly old) post about how the classloader is behaving and the suggested solution (pre Gradle 9, but still)
        // https://stackoverflow.com/questions/44740416/drivermanager-doesnt-see-dependency-in-gradle-custom-plugins-task
        getDriverFiles().each {File file ->
            logger.info("adding classLoader URL " + file.toURI().toURL())
            sqlLoader.addURL(file.toURI().toURL())
        }

        try
        {
            Sql.withInstance(url, user, password, driverClassName) {
                it.execute sql
            }
        }
        catch (Exception e)
        {
            logger.error(e.toString())
        }
    }

    void dropDatabase(String projectPath, DatabaseProperties dbProperties)
    {
        Properties properties = dbProperties.getConfigProperties()
        logger.info("in dropDatabase for ${projectPath}, properties are ${properties}")
        String toDrop = dbProperties.getJdbcDatabase()
        if (toDrop == null || toDrop.equals("labkey"))
        {
            throw new GradleException("Must specify a database that is not 'labkey'")
        }
        else
        {
            DatabaseProperties dropProps = new DatabaseProperties(projectPath, dbProperties)
            // need to connect to the built-in default database in order to drop the database
            dropProps.setJdbcDatabase(dbProperties.getDefaultDatabase())
            dropProps.setJdbcUrlParams("")

            logger.info("Attempting to drop database ${toDrop}")
            execSql(dropProps, "DROP DATABASE \"${toDrop}\";")
        }
    }

}
