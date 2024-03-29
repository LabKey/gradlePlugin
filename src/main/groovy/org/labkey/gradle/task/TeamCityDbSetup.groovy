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
package org.labkey.gradle.task

import org.gradle.api.tasks.Input
import org.labkey.gradle.util.SqlUtils

class TeamCityDbSetup extends DoThenSetup
{
    boolean dbPropertiesChanged = true
    @Input
    boolean dropDatabase = false
    @Input
    boolean testValidationOnly = false

    @Override
    protected void doDatabaseTask()
    {
        databaseProperties.mergePropertiesFromFile()
        if (dropDatabase) {
            if (testValidationOnly){
                logger.info("The 'testValidationOnly' flag is true, not going to drop the database.")
            }
            else {
                SqlUtils.dropDatabase(project, databaseProperties)
            }
        }
        databaseProperties.interpolateCompositeProperties()
        databaseProperties.writeDbProps()
    }

}
