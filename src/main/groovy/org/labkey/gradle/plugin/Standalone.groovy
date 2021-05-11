package org.labkey.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Use to build modules without platform repository and to build distributions without the server repository
 */
class Standalone implements Plugin<Project> {

    void apply(Project project) {
        project.apply plugin: 'org.labkey.build.module'

        if (project.standaloneBuild) {
            project.apply plugin: 'org.labkey.build.tomcat'
            project.apply plugin: 'org.labkey.build.serverDeploy'

            project.allprojects {
                repositories {
                    mavenLocal()
                    maven {
                        url "${project.artifactory_contextUrl}/ext-tools-local"
                    }
                    maven {
                        url "${project.artifactory_contextUrl}/libs-release"
                    }
                    maven {
                        url "${project.artifactory_contextUrl}/libs-snapshot"
                    }
                    jcenter()
                }
            }

            project.dependencies
                    {
                        tomcatJars  "org.labkey.build:tomcat-libs:${project.labkeyVersion}"
                    }
        }

        project.dependencies {

            modules("org.labkey.module:api:${project.labkeyVersion}@module") {
                transitive = true
            }
            modules("org.labkey.module:internal:${project.labkeyVersion}@module") {
                transitive = true
            }
            modules("org.labkey.module:audit:${project.labkeyVersion}@module") {
                transitive = true
            }
            modules("org.labkey.module:core:${project.labkeyVersion}@module") {
                transitive = true
            }

            // include core API
            implementation "org.labkey.api:core:${project.labkeyVersion}"
        }
    }
}
