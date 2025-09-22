package org.labkey.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.labkey.gradle.plugin.extension.TeamCityExtension

import java.util.function.Function

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
