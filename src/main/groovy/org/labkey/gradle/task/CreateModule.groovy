/*
 * Copyright (c) 2017-2018 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.util.BuildUtils

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Creates a new module based on the contents of trunk/server/moduleTemplate. Sources properties from commandline or
 * user input based on the prompt flag.
 * Documented at <a href='https://www.labkey.org/Documentation/wiki-page.view?name=createNewModule'>labkey.org</a>
 */
class CreateModule extends DefaultTask
{
    @TaskAction
    void createModule() {
        String moduleName
        String moduleDestination
        boolean hasManagedSchema
        boolean createTestFiles
        boolean createApiFiles
        boolean deleteExisting

        if (project.hasProperty('moduleName')) {
            moduleName =  project.moduleName
        }
        else {
            project.ant.input(
                    message: "\nEnter the name for your new module: ",
                    addProperty: "new_moduleName"
            )
            moduleName = ant.new_moduleName.trim()
        }
        if (moduleName == null || moduleName == "") {
            throw new GradleException("moduleName is not specified")
        }
        Pattern namePattern = Pattern.compile("[a-zA-Z][a-zA-Z_\\-\\d]*");
        if (!namePattern.matcher(moduleName).matches()) {
            throw new GradleException("Invalid module name: " + moduleName)
        }

        if (project.hasProperty('moduleDestination')) {
            moduleDestination =  project.moduleDestination
        }
        else {
            project.ant.input(
                    message: "\nEnter the location for the new module (absolute or relative to '" + new File("").getAbsolutePath() + "'): ",
                    addProperty: "new_moduleDestination"
            )
            moduleDestination = ant.new_moduleDestination.trim()
        }
        if (moduleDestination == null || moduleDestination == "") {
            throw new GradleException("moduleDestination is not specified")
        }
        File moduleDestinationFile = new File(moduleDestination).getAbsoluteFile()
        if (moduleDestinationFile.exists())
        {
            ant.input(
                    message: "\nModule directory already exists (${moduleDestinationFile.getAbsolutePath()}). " +
                            "Delete directory and create module anyway? (y/N)\"",
                    addProperty: "shouldCreateNewModule"
            )
            deleteExisting = ant.shouldCreateNewModule.toLowerCase().equals("y")
            if (!deleteExisting)
                throw new GradleException("Select a different destination")
        }

        if (project.hasProperty('createFiles')) {
            hasManagedSchema = ((String)project.createFiles).contains('schema')
            createTestFiles = ((String)project.createFiles).contains('test')
            createApiFiles = ((String)project.createFiles).contains('api')
        }
        else {
            project.ant.input(
                    message: "\nWill this module create and manage a database schema? (Y/n)",
                    addProperty: "new_hasManagedSchema"
            )
            hasManagedSchema = !(ant.new_hasManagedSchema.trim().equalsIgnoreCase("n"))

            project.ant.input(
                    message: "\nCreate test stubs (y/N)",
                    addProperty: "new_createTestFiles"
            )
            createTestFiles = ant.new_createTestFiles.trim().equalsIgnoreCase("y")

            project.ant.input(
                    message: "\nCreate API stubs (y/N)",
                    addProperty: "new_createApiFiles"
            )
            createApiFiles = ant.new_createApiFiles.trim().equalsIgnoreCase("y")
        }

        if (deleteExisting) {
            boolean deleted = moduleDestinationFile.deleteDir()
            project.logger.lifecycle("Attempting to delete existing module directory... " + deleted ? "Succeeded" : "Failed")
            if (!deleted) {
                throw new GradleException("Failed to delete existing module directory: ${moduleDestinationFile.getAbsolutePath()}")
            }
        }

        createNewModule(moduleName,
                moduleDestinationFile,
                hasManagedSchema,
                createTestFiles,
                createApiFiles)
    }

    void createNewModule(String moduleName, File moduleDestinationFile, boolean hasManagedSchema, boolean createTestFiles, boolean createApiFiles)
    {
        if (!moduleDestinationFile.mkdir()) {
            throw new GradleException("Failed to create new module directory at ${moduleDestinationFile.getAbsolutePath()}")
        }

        String[] versionParts = BuildUtils.getLabKeyModuleVersion(project.rootProject).split("\\.");
        String version = versionParts[0] + "." + (Integer.parseInt(versionParts[1])-1) + "1"
        Map<String, String> substitutions = [
                'MODULE_DIR_NAME' : moduleName.toLowerCase(),
                'MODULE_LOWERCASE_NAME' : moduleName.toLowerCase(),
                'MODULE_NAME' : StringUtils.capitalize(moduleName),
                'CURRENT_YEAR': Calendar.getInstance().get(Calendar.YEAR).toString(),
                'LABKEY_VERSION_NUMBER': version
        ]

        project.copy({ CopySpec copy ->
            String serverModuleTemplateDir = "${project.rootProject.projectDir}/server/moduleTemplate"
            File templateDir = new File(serverModuleTemplateDir)
            if (templateDir.exists())
                copy.from(serverModuleTemplateDir)
            else
            {
                // This seems a very convoluted way to get to the zip file in the jar file.  Using the classLoader did not
                // work as expected, however.  Following the example from here:
                // https://discuss.gradle.org/t/gradle-plugin-copy-directory-tree-with-files-from-resources/12767/7
                FileTree jarTree = project.zipTree(getClass().getProtectionDomain().getCodeSource().getLocation().toExternalForm())
                File zipFile = jarTree.matching({
                    include "moduleTemplate.zip"
                }).singleFile
                FileTree zipTree = project.zipTree(zipFile);

                copy.from(zipTree)
            }
            copy.into(moduleDestinationFile)
            if (hasManagedSchema)
            {
                copy.exclude("**/MODULE_NAMECodeOnlyModule.java")
            }
            else
            {
                copy.exclude("**/MODULE_NAMESchema.java")
                copy.exclude("**/MODULE_NAMEModule.java")
                copy.exclude("resources/schemas/**")
            }
            if (!createTestFiles)
            {
                copy.exclude("test/**")
            }
            if (!createApiFiles)
            {
                copy.exclude("api-src/**")
            }

            copy.filter({String line ->
                Matcher matcher = Pattern.compile("(@@([^@]+)@@)").matcher(line)
                while(matcher.find()) {
                    line = line.replace(matcher.group(1), substitutions.get(matcher.group(2)))
                }
                return line
            })
        })
        File codeOnlyModule = new File(moduleDestinationFile, "src/org/labkey/MODULE_DIR_NAME/MODULE_NAMECodeOnlyModule.java")
        if (codeOnlyModule.exists()) {
            codeOnlyModule.renameTo(new File(moduleDestinationFile, "src/org/labkey/MODULE_DIR_NAME/MODULE_NAMEModule.java"))
        }
        //copy.rename only looks at file names, rather than files and directories.
        renameCrawler(moduleDestinationFile, substitutions)

        project.logger.quiet("Module created in ${moduleDestinationFile.getAbsolutePath()}")
        project.logger.quiet("Refresh the Gradle window to add this module to your IntelliJ project to start editing the code.")
    }

    void renameCrawler(File currFile, Map<String, String> substitutions) {
        for (File f : currFile.listFiles()) {
            renameCrawler(f, substitutions)
            String name = f.getName()
            substitutions.each({curr, updated ->
                name = name.replace(curr, updated);
            })
            if (!name.equals(f.getName()))
            {
                f.renameTo("${f.getParent()}/${name}")
            }
        }
    }
}