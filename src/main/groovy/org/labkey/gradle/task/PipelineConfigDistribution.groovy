/*
 * Copyright (c) 2017 LabKey Corporation
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
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.util.BuildUtils

/**
 * @deprecated  TODO: Remove once plugin no longer supports 19.1
 */
@Deprecated (forRemoval = true)
class PipelineConfigDistribution extends DefaultTask
{
    public static final String CLASSIFIER = "PipelineConfig"

    @OutputFiles
    List<File> getConfigFiles()
    {
        List<File> files = new ArrayList<>();
        files.add(getConfigFile("zip"))
        files.add(getConfigFile("tar.gz"))
        return files
    }

    private File getConfigFile(String extension)
    {
        return new File("${project.dist.dir}/LabKey${BuildUtils.getDistributionVersion(project)}-${CLASSIFIER}.${extension}")
    }

    @TaskAction
    void doAction()
    {
        ant.zip(destfile: getConfigFile("zip")) {
            zipfileset(dir: "${project.rootProject.projectDir}/server/configs/config-remote",
                    prefix: "remote")
            zipfileset(dir: "${project.rootProject.projectDir}/server/configs/config-cluster",
                    prefix: "cluster")
            zipfileset(dir: "${project.rootProject.projectDir}/server/configs/config-webserver",
                    prefix: "webserver")
        }
        ant.tar(destfile: getConfigFile("tar.gz"),
                longfile:"gnu",
                compression: "gzip") {
            tarfileset(dir: "${project.rootProject.projectDir}/server/configs/config-remote",
                    prefix: "remote") {
                exclude(name: "**/*.bat")
                exclude(name: "**/*.exe")
            }
            tarfileset(dir: "${project.rootProject.projectDir}/server/configs/config-cluster",
                    prefix: "cluster")
            tarfileset(dir: "${project.rootProject.projectDir}/server/configs/config-webserver",
                    prefix: "webserver")
        }
    }

}
