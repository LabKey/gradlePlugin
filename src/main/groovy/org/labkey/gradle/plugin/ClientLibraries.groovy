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


import org.gradle.api.Project
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Delete
import org.labkey.gradle.task.ClientLibsCompress
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames

/**
 * Creates minified, compressed javascript files using the script declarations in a modules .lib.xml file(s).
 */
class ClientLibraries
{
    private static FileTree getLibXmlFiles(Project project)
    {
        return project.fileTree(dir: project.projectDir,
                includes: ["**/*${ClientLibsCompress.LIB_XML_EXTENSION}"],
                // Issue 31367: exclude files that end up in the "out" directory created by IntelliJ
                // exclude node_modules for efficiency
                // exclude 'build' to prevent warnings building standalone modules
                excludes: ["node_modules", "**/out/*", "build"]
        )
    }

    static void addTasks(Project project)
    {
        if (BuildUtils.haveMinificationProject(project.gradle)) {
            String minProjectPath = BuildUtils.getMinificationProjectPath(project.gradle)
            project.tasks.register("compressClientLibs", ClientLibsCompress) {
                ClientLibsCompress task ->
                    task.group = GroupNames.CLIENT_LIBRARIES
                    task.description = 'create minified, compressed javascript file using .lib.xml sources'
                    task.dependsOn(project.tasks.processResources)
                    task.dependsOn(project.project(minProjectPath).tasks.named("npmInstall"))
                    task.xmlFiles = getLibXmlFiles(project)
            }

            project.evaluationDependsOn(minProjectPath)
            project.tasks.assemble.dependsOn(project.tasks.compressClientLibs)

            project.tasks.register("cleanClientLibs", Delete) {
                Delete task ->
                    task.group = GroupNames.CLIENT_LIBRARIES
                    task.description = "Removes ${ClientLibsCompress.getMinificationDir(project)}"
                    task.configure({ DeleteSpec delete ->
                        if (ClientLibsCompress.getMinificationDir(project).exists())
                            delete.delete(ClientLibsCompress.getMinificationDir(project))
                        delete.delete(project.tasks.findByName("compressClientLibs").outputs.files)
                    })
            }
        }

    }
}

