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
package org.labkey.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.Zip
import org.labkey.gradle.plugin.extension.XsdDocExtension
import org.labkey.gradle.task.CreateXsdDocs
import org.labkey.gradle.util.GroupNames

class XsdDoc implements Plugin<Project>
{
    static Provider<Directory> getClientDocsBuildDir(Project project)
    {
        return project.rootProject.layout.buildDirectory.dir("client-api")
    }

    static Directory getXsdDocDirectory(Project project)
    {
        return getClientDocsBuildDir(project).get().dir( "xml-schemas")
    }

    @Override
    void apply(Project project)
    {
        project.extensions.create("xsdDoc", XsdDocExtension)
        addConfiguration(project)
        addTasks(project)
    }

    private void addConfiguration(Project project)
    {
        project.configurations {
            xsdDoc
        }
    }

    private static void addTasks(Project project)
    {
       project.tasks.register("xsddoc", CreateXsdDocs) {
           CreateXsdDocs task ->
               task.group = GroupNames.DOCUMENTATION
               task.description = 'Generating documentation for classes generated from XSD files'
       }

        project.tasks.register("xsdDocZip", Zip) {
            Zip task ->
                task.group = GroupNames.DOCUMENTATION
                task.description = "Package the xsd documentation into a single zip file"
                task.archiveBaseName.set("xml-schemas")
                task.archiveVersion.set(project.getVersion().toString())
                task.archiveExtension.set("zip")
                task.from project.tasks.xsddoc
                task.destinationDirectory.set(getXsdDocDirectory(project))
                task.dependsOn(project.tasks.xsddoc)
        }

        project.tasks.register("cleanXsdDoc", DefaultTask) {
            Task task ->
                task.group = GroupNames.DOCUMENTATION
                task.description = "Remove files created by xsddoc and xsdDocZip tasks"
                task.doFirst({
                    project.delete(project.tasks.xsdDocZip.outputs)
                    project.delete(project.tasks.xsddoc.outputs)
                })
        }
    }
}

