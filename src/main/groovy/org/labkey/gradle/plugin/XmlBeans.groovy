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
package org.labkey.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DeleteSpec
import org.gradle.api.tasks.Delete
import org.labkey.gradle.task.SchemaCompile
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames

/**
 * Class that will convert xsd files into a jar file
 */
class XmlBeans implements Plugin<Project>
{
    public static final String SCHEMAS_DIR = "schemas" // the directory containing the schemas to be compiled
    public static final String CLASS_DIR = "xb" // the name of the directory in build or build/gensrc for the source and class files


    @Override
    void apply(Project project)
    {
        addDependencies(project)
        addTasks(project)
    }

    static boolean isApplicable(Project project)
    {
        return !AntBuild.isApplicable(project) && project.file(SCHEMAS_DIR).exists()
    }

    private void addDependencies(Project project)
    {
        project.configurations
                {
                    xmlbeans
                }

        project.dependencies
                {
                    xmlbeans "org.apache.xmlbeans:xmlbeans:${project.xmlbeansVersion}"
                }

        String schemasProjectPath = BuildUtils.getSchemasProjectPath(project.gradle)
        if (!project.path.equals(schemasProjectPath))
        {
            BuildUtils.addLabKeyDependency(project: project, config: 'xmlbeans', depProjectPath: schemasProjectPath)
        }
    }

    private static void addTasks(Project project)
    {
        project.tasks.register('schemasCompile', SchemaCompile) {
            SchemaCompile task ->
                task.group = GroupNames.XML_SCHEMA
                task.description = "compile XML schemas from directory '$SCHEMAS_DIR' into Java classes"
                task.onlyIf {
                    isApplicable(project)
                }
                // remove the directories containing the generated java files and the compiled classes when we have to make changes.
                task.doFirst( {
                    project.delete(task.getSrcGenDir())
                    project.delete(task.getClassesDir())
                })
        }

        project.tasks.register("cleanSchemasCompile", Delete) {
                Delete task ->
                    task.group = GroupNames.XML_SCHEMA
                    task.description = "remove source and class files generated from xsd files"
                    task.configure (
                {DeleteSpec del ->
                            del.delete "$project.buildDir/$CLASS_DIR",
                                         "$project.labkey.srcGenDir/$CLASS_DIR"
                        }
                    )
        }
    }
}



