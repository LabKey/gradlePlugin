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
package org.labkey.gradle.util

import groovy.sql.Sql
import org.gradle.api.GradleException
import org.gradle.api.Project

import java.sql.Driver
import java.sql.DriverManager

class SqlUtils
{
    static void execSql(Project project, DatabaseProperties params, String sql)
    {
        params.interpolateCompositeProperties()
        String url = params.getJdbcURL()
        String user = params.getJdbcUser()
        String password = params.getJdbcPassword()
        String driverClassName = params.getConfigProperties().getProperty("jdbcDriverClassName")
        project.logger.info("in execSql: url ${url} user ${user} password ${password} driverClassName ${driverClassName}")

        //see http://gradle.1045684.n5.nabble.com/using-jdbc-driver-in-a-task-fails-td1435189.html
        URLClassLoader loader = GroovyObject.class.classLoader

        project.configurations.driver.each {File file ->
            loader.addURL(file.toURI().toURL())
        }
        Class driverClass = loader.loadClass(driverClassName)
        project.logger.info("driverClass is ${driverClass}")

        Driver driverInstance = (Driver) driverClass.newInstance()
        DriverManager.registerDriver(driverInstance)

        Sql db = null
        try
        {
            db = Sql.newInstance(url, user, password)
            db.execute(sql)
        }
        catch (Exception e)
        {
            project.logger.error(e.toString())
        }
        finally
        {
            if (db != null)
                db.close()
        }
    }

    static void dropDatabase(Project project, DatabaseProperties dbProperties)
    {
        Properties properties = dbProperties.getConfigProperties()
        project.logger.info("in dropDatabase for ${project.path}, properties are ${properties}")
        String toDrop = dbProperties.getJdbcDatabase()
        if (toDrop == null || toDrop.equals("labkey"))
        {
            throw new GradleException("Must specify a database that is not 'labkey'")
        }
        else
        {
            DatabaseProperties dropProps = new DatabaseProperties(project, dbProperties)
            // need to connect to the master database in order to drop the database
            dropProps.setJdbcDatabase((String) properties.get('databaseMaster'))
            dropProps.setJdbcUrlParams("")

            project.logger.info("Attempting to drop database ${toDrop}")
            execSql(project, dropProps, "DROP DATABASE \"${toDrop}\";")
        }
    }
}