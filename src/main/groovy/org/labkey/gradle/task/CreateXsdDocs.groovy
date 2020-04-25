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
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
/**
 * Created by susanh on 4/17/17.
 */
class CreateXsdDocs extends DefaultTask
{
    @InputFiles
    List<File> getXsdFiles()
    {
        return project.xsdDoc.xsdFiles
    }

    @OutputDirectory
    File getOutputDirectory()
    {
        return new File("${project.rootProject.buildDir}/client-api/xml-schemas/docs")
    }

    @TaskAction
    void createDocs()
    {
        List<File> xsdFiles = getXsdFiles()
        project.javaexec {exec ->
            exec.main = "com.docflex.xml.Generator"

            exec.classpath project.configurations.xsdDoc

            exec.args = [
                    "-template", "${project.rootDir}/tools/docflex-xml-re-${project.docflexXmlReVersion}/templates/XSDDoc/FramedDoc.tpl",
                    "-p:docTitle", "LabKey XML Schema Reference",
                    "-format", "HTML", // output format
                    "-d", getOutputDirectory(), // output directory
                    "-nodialog", // do not launch the generator GUI
                    "-launchviewer=false", //  do not launch the default viewer for the output file
            ]
            //     Specify one or many data source XML files to be processed
            //     by the specified template. (Both local pathnames and URLs
            //     are allowed.)
            xsdFiles.each {File file ->
                exec.args += file.path
            }
        }
    }
}
