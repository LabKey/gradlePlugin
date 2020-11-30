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
package org.labkey.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.util.BuildUtils

@CacheableTask
class WriteDependenciesFile extends DefaultTask
{
    // we assume that if a version number has changed, we should generate a new dependencies file
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFile
    File globalProperties = project.rootProject.file("gradle.properties")

    @OutputFile
    File dependenciesFile = project.file("resources/credits/dependencies.txt")

    WriteDependenciesFile()
    {
        if (project.file("build.gradle").exists())
        {
            this.inputs.file(project.file("build.gradle"))
        }
        if (project.file("gradle.properties").exists())
        {
            this.inputs.file(project.file("gradle.properties"))
        }
        // add a configuration that has the external dependencies but is not transitive
        project.configurations {
            externalsNotTrans.extendsFrom(project.configurations.external)
            externalsNotTrans {
                transitive = false
            }
        }
        project.configurations.externalsNotTrans.setDescription("Direct external dependencies (not including transitive dependencies)")
        onlyIf {
            !project.configurations.externalsNotTrans.isEmpty()
        }
    }

    @TaskAction
    void writeFile()
    {
        FileOutputStream outputStream = null;
        try
        {
            boolean isApi = project.path.equals(BuildUtils.getApiProjectPath(project.gradle))

            outputStream = new FileOutputStream(dependenciesFile)
            if (isApi)
                outputStream.write("# direct external dependencies of ${project.path} and dependencies of labkey-client-api\n".getBytes())
            else
                outputStream.write("# direct external dependencies for project ${project.path}\n".getBytes())

            Set<String> dependencySet = new HashSet<>();

            project.configurations.externalsNotTrans.each { File file ->
                outputStream.write((file.getName() + "\n").getBytes());
                dependencySet.add(file.getName());
            }
            if (isApi) {
                if (project.configurations.findByName("creditable") != null)
                {
                    project.configurations.creditable.each {
                        if (!dependencySet.contains(it.getName()))
                        {
                            outputStream.write((it.getName() + "\n").getBytes())
                            dependencySet.add(it.getName())
                        }
                    }
                }
            }
        }
        finally
        {
            outputStream.close()
        }
    }
}
