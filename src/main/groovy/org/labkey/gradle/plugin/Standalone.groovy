package org.labkey.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.labkey.gradle.util.BuildUtils

/**
 * Use to build modules without platform repository and to build distributions without the server repository
 * Module will build normally unless the 'standaloneBuild' property is set, in which case, additional plugins will be
 * applied to allow the module to build distributions outside of a server repository.
 */
class Standalone implements Plugin<Project> {

    void apply(Project project) {
        project.apply plugin: 'org.labkey.build.module'

        if (BuildUtils.isStandaloneBuild(project)) {
            project.apply plugin: 'org.labkey.build.tomcat'
            project.apply plugin: 'org.labkey.build.serverDeploy'

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
