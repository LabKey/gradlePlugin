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
import org.gradle.api.GradleException
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.util.BuildUtils

/**
 * N.B.  This task requires that you have the platform/api project source as it needs access to directories in that project
 */
class ServerSideJS extends DefaultTask
{
    @InputDirectory
    File scriptFragmentsDir = project.file("script-fragments")

    @OutputDirectory
    File scriptsDir = project.file("resources/scripts")

    @TaskAction
    void action()
    {
        concatenateExt3JsFiles()
        concatenateExt4JsFiles()
        // copy some of the clientapi into the core module's server-side scripts
        concatenateLabKeyJsFile("ActionURL")
        concatenateLabKeyJsFile("Ajax")
        concatenateLabKeyJsFile("Filter")
        concatenateLabKeyJsFile("Message")
        concatenateLabKeyJsFile("Query")
        concatenateLabKeyJsFile("Report")
        concatenateLabKeyJsFile("Security")
        concatenateLabKeyJsFile("FieldKey")
        concatenateLabKeyJsFile("Utils")
    }

    // create combined Ext.js usable by the core module's server-side scripts
    private void concatenateExt3JsFiles()
    {

        File ext3SrcDir = project.project(BuildUtils.getApiProjectPath(project.gradle)).file("webapp/${project.labkey.ext3Dir}/src")
        if (!ext3SrcDir.exists())
            throw new GradleException("Unable to create server-side javascript files. Missing ext3 source directory: ${ext3SrcDir}")
        if (!scriptsDir.canWrite())
            throw new GradleException("Unable to create server-side javascript files. Output directory ${scriptsDir} not writable.")

        ant.concat(destFile: "${scriptsDir}/Ext.js", force: true)
                {
                    header(file: "${scriptFragmentsDir}/Ext.header.js")
                    fileset(file: new File(ext3SrcDir, "Ext.js"))
                    fileset(file: "${scriptFragmentsDir}/Ext.middle.js")
                    fileset(file: new File(ext3SrcDir, "Observable.js"))
                    fileset(file: new File(ext3SrcDir, "JSON.js"))
                    fileset(file: new File(ext3SrcDir, "Connection.js"))
                    fileset(file: new File(ext3SrcDir, "Format.js"))
                    footer(file: "${scriptFragmentsDir}/Ext.footer.js")
                }
        File destFile = new File("${scriptsDir}/Ext.js")
        if (!destFile.exists())
            throw new GradleException("Output file ${destFile} not created")
    }

    // create a combined Ext4.js usable by the core module's server-side scripts
    private void concatenateExt4JsFiles()
    {
        File ext4SrcDir = project.project(BuildUtils.getApiProjectPath(project.gradle)).file("webapp/${project.labkey.ext4Dir}/src")
        if (!ext4SrcDir.exists())
            throw new GradleException("Unable to create server-side javascript files. Missing ext4 source directory: ${ext4SrcDir}")
        if (!scriptsDir.canWrite())
            throw new GradleException("Unable to create server-side javascript files. Output directory ${scriptsDir} not writable.")

        ant.concat(destFile: "${scriptsDir}/Ext4.js", force: true)
                {
                    header(file: "${scriptFragmentsDir}/Ext4.header.js")
                    fileset(file: new File(ext4SrcDir, "Ext.js"))
                    fileset(file: new File(ext4SrcDir, "lang/Array.js"))
                    fileset(file: new File(ext4SrcDir, "lang/Date.js"))
                    fileset(file: new File(ext4SrcDir, "lang/Number.js"))
                    fileset(file: new File(ext4SrcDir, "lang/Object.js"))
                    fileset(file: new File(ext4SrcDir, "lang/String.js"))
                    fileset(file: new File(ext4SrcDir, "lang/Error.js"))
                    fileset(file: "${scriptFragmentsDir}/Ext4.middle.js")
                    fileset(file: new File(ext4SrcDir, "misc/JSON.js"))
                    footer(file: "${scriptFragmentsDir}/Ext4.footer.js")
                }
        File destFile = new File("${scriptsDir}/Ext4.js")
        if (!destFile.exists())
            throw new GradleException("Output file ${destFile} not created")

    }

    private void concatenateLabKeyJsFile(String baseName)
    {
        File baseFile = project.project(BuildUtils.getApiProjectPath(project.gradle)).file("webapp/clientapi/core/${baseName}.js")
        if (!baseFile.exists())
            throw new GradleException("Unable to create server-side javascript files. Missing source file: ${baseFile}")
        if (!scriptsDir.canWrite())
            throw new GradleException("Unable to create server-side javascript files. Output directory ${scriptsDir} not writable.")

        ant.concat(destFile: "${scriptsDir}/labkey/${baseName}.js", force: true)
                {
                    header(file: "${scriptFragmentsDir}/labkey/${baseName}.header.js")
                    fileset(file: baseFile)
                    footer(file: "${scriptFragmentsDir}/labkey/${baseName}.footer.js")
                }
        File destFile = new File("${scriptsDir}/labkey/${baseName}.js")
        if (!destFile.exists())
            throw new GradleException("Output file ${destFile} not created")
    }
}
