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
import org.gradle.api.file.FileTree
import org.gradle.api.specs.AndSpec
import org.gradle.api.tasks.JavaExec
import org.labkey.gradle.util.GroupNames

/**
 * Used to compile antlr grammars into Java classes using the antlr executable.
 */
class Antlr implements Plugin<Project>
{
    private static final String EXTENSION = ".g"

    @Override
    void apply(Project project)
    {
        addSourceSets(project)
        addTasks(project)
    }

    void addSourceSets(Project project)
    {
        // add the generated source directory as part of the compile source
        project.sourceSets {
            main {
                java {
                    srcDir "${project.labkey.srcGenDir}/antlr"
                }
            }
        }
    }

    void addTasks(Project project)
    {
        FileTree antlrInput = project.fileTree(dir: project.projectDir, includes: ["**/*${EXTENSION}"], excludes: ["out/**/*${EXTENSION}"])

        if (!antlrInput.isEmpty())
        {
            File outputDir = new File("${project.labkey.srcGenDir}/antlr/org/labkey/query/sql/antlr")
            List<Task> antlrTasks = new ArrayList<>();
            antlrInput.files.each() {
                File file ->
                     Task antlrTask = project.task("antlr" + file.getName().substring(0, file.getName().indexOf(EXTENSION)),
                            type: JavaExec,
                            group: GroupNames.CODE_GENERATION,
                            description: "Generate Java classes from " + file.getName(),
                            {
                                inputs.file(file)
                                outputs.dir outputDir

                                // Workaround for incremental build (GRADLE-1483)
                                outputs.upToDateSpec = new AndSpec()

                                main = 'org.antlr.Tool'

                                classpath = project.configurations.antlr

                                args =
                                    [
                                        '-o', outputDir,
                                        file.getPath()
                                    ]
                            }
                    )
                    antlrTasks.add(antlrTask);
            }
            Task antlrTask = project.task("antlr",
                    group: GroupNames.CODE_GENERATION,
                    description: "generate Java classes from all ${EXTENSION} files",
                    dependsOn: antlrTasks
            )
            project.tasks.compileJava.dependsOn(antlrTask)
        }
    }
}
