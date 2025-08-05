package org.labkey.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

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
                    TeamCityPropertiesTask::getLabKeyServerPort,
                "serverPort", project.hasProperty("useSsl") ? "8443" : "8080"))

    @Optional @Input
    final abstract Property<String> contextPath = project.objects.property(String).convention(
            tcPropOrDefault(project,
                    TeamCityPropertiesTask::getLabKeyContextPath,
                    "contextPath",
                    ""))

    @Optional @Input
    final abstract Property<String> shutdownPort = project.objects.property(String).convention(
            tcPropOrDefault(project,
                    TeamCityPropertiesTask::getLabKeyServerShutdownPort,
                    "shutdownPort",
                    "8081"))
    @Optional @Input
    final abstract Property<String> keyStore = project.objects.property(String).convention(
            tcPropOrDefault(project,
                    TeamCityPropertiesTask::getLabKeyServerKeystore,
                    "keyStore",
                    "/opt/teamcity-agent/localhost.keystore"))

    @Optional @Input
    final abstract Property<String> keyStorePassword = project.objects.property(String).convention(
            tcPropOrDefault(project,
                    TeamCityPropertiesTask::getLabKeyServerKeystorePassword,
                    "keyStorePassword",
                    "changeit"))

    @Optional @Input
    final abstract Property<String> labKeyServer = project.objects.property(String).convention(getLabKeyServer(project))



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

    static boolean isOnTeamCity(Project project)
    {
        return project.hasProperty('teamcity')
    }

    static Object getTeamCityProperty(Project project, String name, Object defaultValue)
    {
        if (isOnTeamCity(project))
            return project.teamcity[name] != null ? project.teamcity[name] : defaultValue
        else if (project.hasProperty(name))
            return project.property(name)
        else
            return defaultValue
    }

    static Properties getTeamCityProperties(Project project)
    {
        if (isOnTeamCity(project))
        {
            def tcProps = new Properties()
            tcProps.putAll(project.teamcity)
            return tcProps
        }
        else
            return new Properties()
    }

    static String getLabKeyServer(Project project)
    {
        return getTeamCityProperty(project, "labkey.server", "http://localhost")
    }

    static String getLabKeyContextPath(Project project)
    {
        return getTeamCityProperty(project, "labkey.contextpath", null)
    }

    static String getLabKeyServerPort(Project project)
    {
        return getTeamCityProperty(project, 'tomcat.port', null)
    }

    static String getLabKeyServerShutdownPort(Project project)
    {
        return getTeamCityProperty(project, 'tomcat.shutdown', null)
    }

    static String getLabKeyServerKeystore(Project project)
    {
        return getTeamCityProperty(project, 'labkey.keystore', null)
    }

    static String getLabKeyServerKeystorePassword(Project project)
    {
        return getTeamCityProperty(project, 'labkey.keystore.password', null)
    }

    static String getLabKeyUsername(Project project)
    {
        return getTeamCityProperty(project, "labkey.server.email", "teamcity@labkey.test")
    }

    static String getLabKeyPassword(Project project)
    {
        return getTeamCityProperty(project, "labkey.server.password", "We'reSo\$tr0ng@yekbal1!")
    }
}
