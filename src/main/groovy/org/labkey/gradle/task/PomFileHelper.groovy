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


import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.labkey.gradle.plugin.ServerBootstrap
import org.labkey.gradle.plugin.extension.LabKeyExtension

/**
 * Helper methods for generating a pom file using the maven-publish plugin.
 */
class PomFileHelper
{
    public static final String LABKEY_ORG_URL = "http://www.labkey.org"

    private Properties pomProperties = new Properties()
    private Project project
    private boolean isModulePom

    PomFileHelper (Properties pomProperties, Project project, boolean isModulePom)
    {
        this.pomProperties = pomProperties
        this.project = project
        this.isModulePom = isModulePom
    }

    static Closure getLabKeyTeamDevelopers() {
        return {
            developer {
                id = 'labkey-team'
                name = 'The LabKey Development Team'
                organization = 'LabKey.org'
                organizationUrl = LABKEY_ORG_URL
            }
        }
    }

    static Closure getApacheLicense() {
        return {
            license {
                name = 'The Apache License, Version 2.0'
                url = 'http://www.apache.org/licenses/LICENSE-2.0.txt'
                distribution = 'repo'
            }
        }
    }

    Closure getLicense() {
        return {
            license {
                name = pomProperties.getProperty("License")
                url = pomProperties.getProperty("LicenseURL")
                distribution = 'repo'
            }
        }
    }

    static Closure getLabKeyOrganization() {
        return {
            name = "LabKey"
            url = LABKEY_ORG_URL
        }
    }

    Closure getOrganization() {
        return {
            name = pomProperties.getProperty("Organization")
            url = pomProperties.getProperty("OrganizationURL")
        }
    }

    static Closure getLabKeyGitScm() {
        return {
            connection = 'scm:git:https://github.com/LabKey/'
            developerConnection = 'scm:git:https://github.com/LabKey/'
            url = 'scm:git:https://github.com/LabKey/'
        }
    }

    private DependencySet getDependencySet()
    {
        if (isModulePom)
            return project.configurations.modules.allDependencies
        else if (project.configurations.findByName('apiImplementation') != null)
            return project.configurations.apiImplementation.allDependencies
        else
            return project.configurations.api.allDependencies
    }

    void getDependencyClosure(Node node, boolean isModulePom)
    {
        // perhaps do this first for module pom and last otherwise?
        if (isModulePom)
            modifyModuleDependencies(node) // we are assured to have a dependencies node as a result of this
        else if (node.dependencies.isEmpty())
            node.appendNode('dependencies')

        def dependenciesNode = node.dependencies.first()

        // add in the dependencies from the external configuration as well
        getDependencySet().each {
            def depNode = dependenciesNode.appendNode("dependency")

            depNode.appendNode("artifactId", it.name)
            depNode.appendNode("version", it.version)
            depNode.appendNode("scope", pomProperties.getProperty("scope"))
            if (isModulePom){
                depNode.appendNode("type", pomProperties.getProperty("type"))
                depNode.appendNode("groupId", pomProperties.getProperty("groupId"))
            }
            else
            {
                depNode.appendNode("groupId", it.group)
            }
        }
        if (!isModulePom)
            modifyApiDependencies(node)
        // TODO should we remove the dependencies node if it still has nothing in it?
    }

    private static void modifyModuleDependencies(Node root)
    {
        if (root.dependencies.isEmpty())
        {
            root.appendNode('dependencies') // assure we have a dependencies node
        }
        else // Remove all dependencies automatically added by gradle (tomcat lib dependencies, etc.)
        {
            root.dependencies.first().replaceNode(new Node(null, 'dependencies'))
        }
    }

    private void modifyApiDependencies(Node root)
    {
        def dependencies = root.dependencies
        if (dependencies.isEmpty())
            root.appendNode('dependencies')
        else
        {
            List<Node> toRemove = []

            dependencies.first().each {
                // remove the tomcat dependencies with no version specified because we cannot know which version of tomcat is in use
                if (it.get("groupId").first().value().equals("org.apache.tomcat") &&
                        it.get("version").isEmpty())
                {
                    // added to detect if we actually need this anymore
                    project.quiet("${project.path} removing unversioned dependency on org.apache.tomcat.${it.get('artifactId').first().value()}")
                    toRemove.add(it)
                }


                if (it.get('groupId').first().value().equals(LabKeyExtension.LABKEY_GROUP)) {
                    String artifactId = it.get('artifactId').first().value()

                    if (artifactId.equals("java"))
                        it.get('artifactId').first().setValue(['labkey-client-api'])
                    else if (artifactId.equals("bootstrap"))
                        it.get('artifactId').first().setValue(ServerBootstrap.JAR_BASE_NAME)

                    if ((artifactId.equals("java") || artifactId.equals("labkey-client-api"))) {
                        // labkey-client-api group was org.labkey until it was released with its own version number,
                        // at which point it was put in the org.labkey.api group
                        if (project.hasProperty("labkeyClientApiVersion")) {
                            it.get('groupId').first().setValue(LabKeyExtension.API_GROUP)
                        }
                    } else if (!artifactId.equals("bootstrap")) // everything else except bootstrap is in org.labkey.api
                        it.get('groupId').first().setValue(LabKeyExtension.API_GROUP)
                }
            }
            toRemove.each {
                root.dependencies.first().remove(it)
            }
        }
    }
}
