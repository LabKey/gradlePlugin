package org.labkey.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
/**
 * This task creates a pom file in a location that artifactory expects it when publishing.  It is meant to
 * replace the task created by the (incubating) maven-publish plugin since for whatever reason that task does
 * not pull in the dependencies (and it is sometimes, mysteriously, removed from the dependency list for
 * the artifactoryPublish task).
 */
class PomFile extends DefaultTask
{
    String artifactCategory = "libs"
    Properties pomProperties = null

    @OutputFile
    File pomFile = new File(project.buildDir, "publications/${artifactCategory}/pom-default.xml")

    @TaskAction
    void writePomFile()
    {
            project.pom {
                withXml {
                    asNode().get('artifactId').first().setValue((String) pomProperties.getProperty("ArtifactId", project.name))
                    // remove the tomcat dependencies with no version specified because we cannot know which version of tomcat is in use
                    List<Node> toRemove = []
                    def dependencies = asNode().dependencies
                    if (!dependencies.isEmpty())
                    {
                        dependencies.first().each {
                            if (it.get("groupId").first().value().first().equals("org.apache.tomcat") &&
                                    it.get("version").isEmpty())
                                toRemove.add(it)
                            if (it.get('groupId').first().value().first().equals("org.labkey") && it.get('artifactId').first().value().first().equals("java"))
                            {
                                it.get('artifactId').first().setValue(['labkey-client-api'])
                            }
                        }
                        toRemove.each {
                            asNode().dependencies.first().remove(it)
                        }
                        // FIXME it's possible to have external dependencies but no dependencies.
                        // add in the dependencies from the external configuration as well
                        def dependenciesNode = asNode().dependencies.first()
                        project.configurations.external.dependencies.each {
                            def depNode = dependenciesNode.appendNode("dependency")
                            depNode.appendNode("groupId", it.group)
                            depNode.appendNode("artifactId", it.name)
                            depNode.appendNode("version", it.version)
                            depNode.appendNode("scope", "compile")
                        }
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
            }.writeTo(pomFile)
    }
}
