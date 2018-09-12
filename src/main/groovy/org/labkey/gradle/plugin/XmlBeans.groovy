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
import org.gradle.api.file.DeleteSpec
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Jar
import org.labkey.gradle.plugin.extension.XmlBeansExtension
import org.labkey.gradle.task.SchemaCompile
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames
/**
 * Class that will convert xsd files into a jar file
 */
class XmlBeans implements Plugin<Project>
{
    public static final String CLASSIFIER = "schemas"

    @Override
    void apply(Project project)
    {
        project.extensions.create("xmlBeans", XmlBeansExtension)

        addDependencies(project)
        addTasks(project)
        addArtifacts(project)

    }

    static boolean isApplicable(Project project)
    {
        return !AntBuild.isApplicable(project) && project.file(project.xmlBeans.schemasDir).exists()
    }

    private void addDependencies(Project project)
    {
        project.configurations
                {
                    xmlbeans
                    xmlSchema // Used for declaring artifacts
                }
        String schemasProjectPath = BuildUtils.getProjectPath(project.gradle, "schemasProjectPath", ":schemas")
        if (project.findProject(schemasProjectPath) != null && project.findProject(schemasProjectPath).projectDir.exists())
        {
            if (!project.path.equals(schemasProjectPath))
            {
                BuildUtils.addLabKeyDependency(project: project, config: 'xmlbeans', depProjectPath: schemasProjectPath, depProjectConfig: 'xmlSchema', depVersion: project.labkeyVersion)
            }
        }
        else
        {
            String apiProjectPath = BuildUtils.getProjectPath(project.gradle, "apiProjectPath", ":server:api")

            if (!project.path.equals(apiProjectPath) && project.project(apiProjectPath).configurations.findByName("xmlbeans") != null)
            {
                BuildUtils.addLabKeyDependency(project: project, config: 'xmlbeans', depProjectPath: apiProjectPath, depProjectConfig: 'xmlbeans', depVersion: project.labkeyVersion)
            }
        }
        project.dependencies
                {
                    xmlbeans "org.apache.xmlbeans:xmlbeans:${project.xmlbeansVersion}"
                }
    }

    private void addArtifacts(Project project)
    {
        project.artifacts {
            xmlSchema project.tasks.schemasJar
        }
    }

    private void addTasks(Project project)
    {
        Task schemasCompile = project.task('schemasCompile',
                group: GroupNames.XML_SCHEMA,
                type: SchemaCompile,
                description: "compile XML schemas from directory '$project.xmlBeans.schemasDir' into Java classes"
        )
        schemasCompile.onlyIf {
            isApplicable(project)
        }

        // remove the directories containing the generated java files and the compiled classes when we have to make changes.
        project.tasks.schemasCompile.doFirst( {SchemaCompile task ->
            project.delete(task.getSrcGenDir())
            project.delete(task.getClassesDir())
        })

        Task schemasJar = project.task('schemasJar',
                group: GroupNames.XML_SCHEMA,
                type: Jar,
                description: "produce schemas jar file from directory '$project.xmlBeans.classDir'",
                {Jar jar ->
                    jar.classifier CLASSIFIER
                    jar.from "$project.buildDir/$project.xmlBeans.classDir"
                    jar.exclude '**/*.java'
                    jar.baseName = project.name.equals("schemas") ? "schemas": "${project.name}_schemas"
                    jar.destinationDir = project.file(project.labkey.explodedModuleLibDir)
                }
        )
        schemasJar.dependsOn(project.tasks.schemasCompile)
        schemasJar.onlyIf
                {
                    isApplicable(project)
                }

        project.task("cleanSchemasJar",
                group: GroupNames.XML_SCHEMA,
                type: Delete,
                description: "remove schema jar file",
                { DeleteSpec del ->
                    del.delete "$schemasJar.destinationDir/$schemasJar.archiveName"
                }
        )

        project.task("cleanSchemasCompile",
                group: GroupNames.XML_SCHEMA,
                type: Delete,
                description: "remove source and class files generated from xsd files",
                {DeleteSpec del ->
                    del.delete "$project.buildDir/$project.xmlBeans.classDir",
                                "$project.labkey.srcGenDir/$project.xmlBeans.classDir"
                }
        )
    }
}



