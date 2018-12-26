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
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Created by susanh on 4/5/16.
 */
class JspCompile2Java extends DefaultTask
{
    @OutputDirectory
    File getClassesDirectory()
    {
        return new File("${project.buildDir}/${project.jspCompile.classDir}")
    }

    @OutputDirectory
    File getWebAppDirectory()
    {
        return new File("${project.buildDir}/${project.jspCompile.tempDir}/webapp")
    }

    @TaskAction
    void compile() {

        File uriRoot = getWebAppDirectory()
        project.logger.info("${project.path} Compiling jsps to Java into ${uriRoot.getAbsolutePath()}")
        if (!uriRoot.exists())
            if (!uriRoot.mkdirs())
                project.logger.error("${project.path}: problem creating directory ${uriRoot.getAbsolutePath()}")
        File classesDir = getClassesDirectory()
        if (!classesDir.exists())
            if (!classesDir.mkdirs())
                project.logger.error("${project.path}: problem creating directory ${classesDir.getAbsolutePath()}")
        ant.taskdef(
                name: 'jasper',
                classname: 'org.apache.jasper.JspC',
                classpath: project.configurations.jspCompile.asPath
        )
        ant.jasper(
                uriroot: "${uriRoot.getAbsolutePath()}",
                outputDir: getClassesDirectory(),
                package: "org.labkey.jsp.compiled",
                compilerTargetVM: project.targetCompatibility,
                compilerSourceVM: project.sourceCompatibility,
                compile: false,
                listErrors: true
        )
    }
}
