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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.Copy
import org.labkey.gradle.plugin.extension.JsDocExtension
import org.labkey.gradle.task.CreateJsDocs
import org.labkey.gradle.util.GroupNames
import org.labkey.gradle.util.PropertiesUtils

import java.util.regex.Matcher
/**
 * Plugin that provides tasks for created JavaScript documentation using jsDoc tools
 */
class JsDoc implements Plugin<Project>
{
    @Override
    void apply(Project project)
    {
        project.extensions.create("jsDoc", JsDocExtension)
        project.jsDoc.root = "${project.rootDir}/tools/jsdoc-toolkit/"
        project.jsDoc.outputDir = new File("${project.rootProject.buildDir}/client-api/javascript/docs")
        addTasks(project)
    }

    private static void addTasks(Project project)
    {
        project.tasks.register('jsdocTemplate', Copy) {
            Copy task ->
                task.description = "insert the proper version number into the JavaScript documentation"
                task.configure
                        { CopySpec copy ->
                            copy.from project.file("${project.jsDoc.root}/templates/jsdoc")
                            copy.filter( { String line ->
                                Matcher matcher = PropertiesUtils.PROPERTY_PATTERN.matcher(line);
                                String newLine = line;
                                while (matcher.find())
                                {
                                    if (matcher.group(1).equals("product.version"))
                                        newLine = newLine.replace(matcher.group(), (String) project.version)
                                }
                                return newLine;

                            })
                            copy.destinationDir = new File((String) "${project.jsDoc.root}/templates/jsdoc_substituted")
                        }
        }

         project.tasks.register("jsdoc", CreateJsDocs) {
             CreateJsDocs task ->
                 task.group = GroupNames.DOCUMENTATION
                 task.description = 'Generating Client API docs'
                 task.dependsOn(project.tasks.jsdocTemplate)
         }
    }
}

