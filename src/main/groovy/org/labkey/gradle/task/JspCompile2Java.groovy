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

import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
class JspCompile2Java extends DefaultTask
{
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputDirectory
    File webappDirectory

    @OutputDirectory
    File getClassesDirectory()
    {
        return new File("${project.buildDir}/${project.jspCompile.classDir}")
    }

    @TaskAction
    void compile() {

        if (!webappDirectory.exists())
        {
            project.logger.info("${webappDirectory.getAbsolutePath()}: no such file or directory.  Nothing to do here.")
            return;
        }

        project.logger.info("${project.path} Compiling jsps to Java from ${webappDirectory.getAbsolutePath()}")
        String[] extensions = ["jsp"]
        project.logger.info("${project.path}: Jsp files in ${webappDirectory.getAbsolutePath()}")
        FileUtils.listFiles(webappDirectory, extensions, true).forEach({
            File file ->
                project.logger.info(file.getAbsolutePath())
        });

        File classesDir = getClassesDirectory()

        if (!classesDir.exists() && !classesDir.mkdirs())
            throw new GradleException("${project.path}: problem creating output directory ${classesDir.getAbsolutePath()}")

        ant.taskdef(
                name: 'jasper',
                classname: 'org.apache.jasper.JspC',
                classpath: project.configurations.jspCompileClasspath.asPath
        )
        ant.jasper(
                uriroot: "${webappDirectory.getAbsolutePath()}",
                outputDir: getClassesDirectory(),
                package: "org.labkey.jsp.compiled",
                compilerTargetVM: project.targetCompatibility,
                compilerSourceVM: project.sourceCompatibility,
                compile: false,
                listErrors: true
        )
    }
}
