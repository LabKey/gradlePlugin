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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.*
import org.labkey.gradle.util.BuildUtils

import javax.inject.Inject

@CacheableTask
abstract class JspCompile2Java extends DefaultTask
{
    public static final String CLASSES_DIR = "jspTempDir/classes"

    private FileSystemOperations fileSystemOperations

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputDirectory
    File webappDirectory

    @OutputDirectory
    final abstract DirectoryProperty classesDirectory = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir(CLASSES_DIR).get())

    @InputFiles
    @CompileClasspath
    abstract ConfigurableFileCollection getCompileClasspath()

    @Inject
    JspCompile2Java(FileSystemOperations fs) {
        fileSystemOperations = fs
    }


    @TaskAction
    void compile() {

        if (!webappDirectory.exists())
        {
            project.logger.info("${webappDirectory.getAbsolutePath()}: no such file or directory.  Nothing to do here.")
            return
        }

        logger.info("Compiling jsps to Java from ${webappDirectory.getAbsolutePath()}")
        String[] extensions = ["jsp"]
        logger.info("Jsp files in ${webappDirectory.getAbsolutePath()}")
        FileUtils.listFiles(webappDirectory, extensions, true).forEach({
            File file ->
                logger.info(file.getAbsolutePath())
        })

        fileSystemOperations.delete( {
            it.delete(classesDirectory.get())
        })

        File classesDir = classesDirectory.get().asFile

        if (!classesDir.mkdirs())
            throw new GradleException("${project.path}: problem creating output directory ${classesDir.getAbsolutePath()}")

        ant.taskdef(
                name: 'jasper',
                classname: 'org.apache.jasper.JspC',
                classpath: getCompileClasspath().getAsPath()
        )
        ant.jasper(
                uriroot: "${webappDirectory.getAbsolutePath()}",
                outputDir: getClassesDirectory().get(),
                package: "org.labkey.jsp.compiled",
                compilerTargetVM: project.targetCompatibility,
                compilerSourceVM: project.sourceCompatibility,
                compile: false,
                listErrors: true
        )
    }
}
