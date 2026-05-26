/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.util.PropertiesUtils

import java.util.regex.Matcher

@CacheableTask
abstract class ModuleXmlFile extends DefaultTask
{
    @Input
    abstract MapProperty<Object, Object> getModuleProperties()

    @OutputFile
    public abstract RegularFileProperty moduleXmlFile = project.objects.fileProperty().convention(project.layout.buildDirectory.file("${LabKeyExtension.EXPLODED_MODULE_DIR_NAME}/config/module.xml").get())

    File getModuleXmlFile()
    {
        return moduleXmlFile.get().asFile
    }

    @TaskAction
    void action() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("module.template.xml")
        if (is == null)
        {
            throw new GradleException("Could not find 'module.template.xml' as resource file")
        }

        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(getModuleXmlFile()))
        Map<Object, Object> props = getModuleProperties().get()
        is.readLines().each {
            String line ->
                Matcher matcher = PropertiesUtils.PROPERTY_PATTERN.matcher(line)
                String newLine = line
                while (matcher.find())
                {
                    String prop = (String) props.get(matcher.group(1))
                    if (prop == null)
                        prop = ""
                    newLine = newLine.replace(matcher.group(), prop)
                }
                writer.println(newLine)
        }
        writer.close()
        is.close()
    }
}
