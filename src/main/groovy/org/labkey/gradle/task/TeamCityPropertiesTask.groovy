/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.UntrackedTask
import org.labkey.gradle.plugin.extension.TeamCityExtension

import java.util.function.Function

@UntrackedTask(because="No tracked output")
abstract class TeamCityPropertiesTask extends DefaultTask
{
    @Input
    final abstract Property<Boolean> useSsl = project.objects.property(Boolean).convention(project.hasProperty("useSsl"))

    @Optional @Input
    final abstract Property<Boolean> isOnTeamCity = project.objects.property(Boolean).convention(project.hasProperty("teamCity"))

    @Optional @Input
    final abstract Property<String> labKeyServerPort = project.objects.property(String).convention(
            tcPropOrDefault(project,
                    TeamCityExtension::getLabKeyServerPort,
                "serverPort", project.hasProperty("useSsl") ? "8443" : "8080"))

    @Optional @Input
    final abstract Property<String> contextPath = project.objects.property(String).convention(
            tcPropOrDefault(project,
                    TeamCityExtension::getLabKeyContextPath,
                    "contextPath",
                    ""))

    @Optional @Input
    final abstract Property<String> shutdownPort = project.objects.property(String).convention(
            tcPropOrDefault(project,
                    TeamCityExtension::getLabKeyServerShutdownPort,
                    "shutdownPort",
                    "8081"))
    @Optional @Input
    final abstract Property<String> keyStore = project.objects.property(String).convention(
            tcPropOrDefault(project,
                    TeamCityExtension::getLabKeyServerKeystore,
                    "keyStore",
                    "/opt/teamcity-agent/localhost.keystore"))

    @Optional @Input
    final abstract Property<String> keyStorePassword = project.objects.property(String).convention(
            tcPropOrDefault(project,
                    TeamCityExtension::getLabKeyServerKeystorePassword,
                    "keyStorePassword",
                    "changeit"))

    @Optional @Input
    final abstract Property<String> labKeyServer = project.objects.property(String).convention(TeamCityExtension.getLabKeyServer(project))

    @Optional @Input
    final abstract Property<String> tomcatJavaHome = project.objects.property(String).convention(
            tcPropOrDefault(project,
                    TeamCityExtension::getTomcatJavaHome,
                    "tomcatJavaHome",
                    System.getenv("JAVA_HOME")
            )
    )

    protected static String tcPropOrDefault(Project project, Function<Project, String> tcPropertyFunc, String projectPropertyName, String defaultValue)
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
