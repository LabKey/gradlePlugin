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
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * This task creates a pom file in a location that artifactory expects it when publishing.  It is meant to
 * replace the task created by the (incubating) maven-publish plugin since for whatever reason that task does
 * not pull in the dependencies (and it is sometimes, mysteriously, removed from the dependency list for
 * the artifactoryPublish task).
 *
 * The pom file produced here should have all the .module dependencies and, where appropriate, any external
 * .jar file dependencies not captured in the .module files themselves.
 *
 * FIXME For this to work, we'll need to convert all modules to declare their module dependencies within their respective build.gradle files
 */
class ModulePomFile extends DefaultTask
{
    String artifactCategory = "module"
    Properties pomProperties = new Properties()

    @OutputFile
    File getPomFile()
    {
        return new File(project.buildDir, "publications/${artifactCategory}/pom-default.xml")
    }

    @TaskAction
    void writePomFile()
    {
            project.pom {
                withXml {
                    asNode().get('artifactId').first().setValue((String) pomProperties.getProperty("ArtifactId", project.name))

                    def dependencies = asNode().dependencies
                    def dependenciesNode
                    if (!dependencies.isEmpty())
                    {
                        dependenciesNode = asNode().dependencies.first()
                    }
                    else
                    {
                        dependenciesNode = asNode().appendNode("dependencies")
                    }
                    project.configurations.module.allDependencies.each {
                        def depNode = dependenciesNode.appendNode("dependency")
                        depNode.appendNode("groupId", it.group)
                        depNode.appendNode("artifactId", it.name)
                        depNode.appendNode("version", it.version)
                        depNode.appendNode("scope", "compile")
                    }

                    if (pomProperties.getProperty("Organization") != null || pomProperties.getProperty("OrganizationURL") != null)
                    {
                        def orgNode = asNode().appendNode("organization")
                        if (pomProperties.getProperty("Organization") != null)
                            orgNode.appendNode("name", pomProperties.getProperty("Organization"))
                        if (pomProperties.getProperty("OrganizationURL") != null)
                            orgNode.appendNode("url", pomProperties.getProperty("OrganizationURL"))
                    }
                    if (pomProperties.getProperty("Description") != null)
                        asNode().appendNode("description", pomProperties.getProperty("Description"))
                    if (pomProperties.getProperty("URL") != null)
                        asNode().appendNode("url", pomProperties.getProperty("URL"))
                    if (pomProperties.getProperty("License") != null || pomProperties.getProperty("LicenseURL") != null)
                    {
                        def licenseNode = asNode().appendNode("licenses").appendNode("license")
                        if (pomProperties.getProperty("License") != null)
                            licenseNode.appendNode("name", pomProperties.getProperty("License"))
                        if (pomProperties.getProperty("LicenseURL") != null)
                            licenseNode.appendNode("url", pomProperties.getProperty("LicenseURL"))
                        licenseNode.appendNode("distribution", "repo")
                    }
                }
            }.writeTo(getPomFile())
    }
}
