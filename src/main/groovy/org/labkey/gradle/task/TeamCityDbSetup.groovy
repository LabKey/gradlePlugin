/*
 * Copyright (c) 2020-2026 LabKey Corporation
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

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.UntrackedTask
import org.labkey.gradle.util.DatabaseProperties

@UntrackedTask(because="External side effects are not cacheable")
abstract class TeamCityDbSetup extends DoThenSetup
{
    @Input
    boolean dropDatabase = false
    @Input
    boolean testValidationOnly = false

    @Override
    protected void doDatabaseTask()
    {
        databaseProperties.mergePropertiesFromFile(chosenPropsFile.get().asFile)
        if (dropDatabase) {
            if (testValidationOnly) {
                logger.info("The 'testValidationOnly' flag is true, not going to drop the database.")
            }
            else {
                dropDatabase(getPath(), databaseProperties)
            }
        }
        databaseProperties.interpolateCompositeProperties()
        writeDbProps()
    }

    void writeDbProps()
    {
        writeDatabaseProperty(DatabaseProperties.JDBC_URL_PROP, databaseProperties.getJdbcURL())
        writeDatabaseProperty(DatabaseProperties.JDBC_USER_PROP, databaseProperties.getJdbcUser())
        writeDatabaseProperty(DatabaseProperties.JDBC_PASSWORD_PROP, databaseProperties.getJdbcPassword())
    }

    private void writeDatabaseProperty(String name, String value)
    {
        this.ant.propertyfile(
                file: chosenPropsFile.get().asFile
        )
                {
                    entry( key: name, value: value)
                }
    }
}
