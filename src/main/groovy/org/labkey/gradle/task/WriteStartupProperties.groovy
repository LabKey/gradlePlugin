/*
 * Copyright (c) 2026 LabKey Corporation
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

import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.labkey.gradle.util.PropertiesUtils

import java.nio.charset.StandardCharsets

/**
 * Writes a startup properties file into the deployed server's startup directory.
 */
@DisableCachingByDefault(because="Output is in the deploy directory")
abstract class WriteStartupProperties extends DefaultTask
{
    @OutputFile
    abstract RegularFileProperty getPropertiesFile()

    @Input @Optional
    abstract Property<String> getPropertiesContent()

    // @InputFiles rather than @InputFile because the database config doesn't exist until 'pickPg'/'pickMSSQL'
    @InputFiles @Optional @PathSensitive(PathSensitivity.NONE)
    abstract RegularFileProperty getDatabasePropertiesFile()

    @Input @Optional
    abstract Property<String> getDatabasePropertyPrefix()

    @TaskAction
    void writeProperties()
    {
        File file = propertiesFile.get().asFile
        String content = [readDatabaseStartupProperties(), propertiesContent.getOrNull()]
                .findAll { it != null && !it.isBlank() }
                .join("\n")
        if (content.isBlank())
        {
            logger.info("No properties to write. ${file} not created.")
            file.delete()
            return
        }
        logger.info("Writing startup properties to ${file}")
        FileUtils.write(file, content, StandardCharsets.UTF_8)
    }

    private String readDatabaseStartupProperties()
    {
        if (!databasePropertiesFile.isPresent() || !databasePropertyPrefix.isPresent())
            return null
        String prefix = databasePropertyPrefix.get()
        Properties databaseProperties = PropertiesUtils.readProperties(databasePropertiesFile.get().asFile)
        Properties startupProperties = new Properties()
        databaseProperties.stringPropertyNames()
                .findAll { it.startsWith(prefix) }
                .each { startupProperties.setProperty(it.substring(prefix.length()), databaseProperties.getProperty(it)) }
        if (startupProperties.isEmpty())
            return null
        StringWriter writer = new StringWriter()
        startupProperties.store(writer, null)
        // Drop the timestamp comment that 'store' always writes
        return writer.toString().readLines().findAll { !it.startsWith("#") }.join("\n")
    }
}
