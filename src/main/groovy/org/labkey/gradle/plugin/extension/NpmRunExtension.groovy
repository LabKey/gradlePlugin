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
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService

import static org.gradle.api.services.BuildServiceParameters.None

class NpmRunExtension
{
    String clean = "clean"
    String setup = "setup"
    String buildProd = "build-prod"
    String buildDev = "build"

    // https://docs.gradle.org/current/userguide/build_services.html#concurrent_access_to_the_service
    final Provider<BuildService<None>> npmRunLimit

    NpmRunExtension(Project project) {
        npmRunLimit = project.getGradle().getSharedServices().registerIfAbsent("npmRunLimit", NpmRunLimit.class, spec -> {
            if (project.hasProperty("npmRunLimit"))
                spec.getMaxParallelUsages().set(Integer.valueOf(project.property("npmRunLimit")))
        })
    }
}

abstract class NpmRunLimit implements BuildService<None> { }
