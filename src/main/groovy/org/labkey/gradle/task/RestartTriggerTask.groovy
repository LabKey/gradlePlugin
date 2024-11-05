package org.labkey.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.labkey.gradle.util.BuildUtils

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat

abstract class RestartTriggerTask extends DefaultTask
{
    public static final String RESTART_FILE_NAME = ".restartTrigger"

    @Input
    final abstract Property<String> useLocalBuild = project.objects.property(String).convention(project.hasProperty('useLocalBuild') ? (String) project.property('useLocalBuild') : null)

    @OutputDirectory
    final abstract DirectoryProperty triggerFileDir = BuildUtils.getRootBuildDirectoryProperty(project, "deploy/modules")

    void updateRestartTriggerFile()
    {
        if (useLocalBuild.get() == null || "false" == useLocalBuild.get())
            return

        if (!triggerFileDir.get().asFile.exists())
            return

        File triggerFile = new File(triggerFileDir.get().asFile, RESTART_FILE_NAME)
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(triggerFile), StandardCharsets.UTF_8))
        {
            writer.write(SimpleDateFormat.getDateTimeInstance().format(new Date()))
        }
    }
}
