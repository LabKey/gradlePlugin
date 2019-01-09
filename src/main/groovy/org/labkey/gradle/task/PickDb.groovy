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

import org.gradle.api.file.CopySpec

/**
 * Created by susanh on 8/11/16.
 */
class PickDb extends DoThenSetup
{
    String dbType
    File dir = project.project(":server").projectDir

    Closure<Void> fn = {

        //copies the correct config file.
        project.copy({ CopySpec copy ->
            copy.from "${dir}/configs"
            copy.into dir
            copy.include "${dbType}.properties"
            copy.rename { String fileName ->
                fileName.replace(dbType, "config")
            }
        })

        super.getFn().run();
    }
}
