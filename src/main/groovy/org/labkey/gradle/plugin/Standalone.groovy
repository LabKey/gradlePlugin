package org.labkey.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.labkey.gradle.util.BuildUtils

/**
 * Use to build distributions without the server repository.
 * Apply to the 'distributions' project of a standalone module.
 * Plugin will no-op if target project doesn't match 'gradle.ext.serverProjectPath'
 */
class Standalone implements Plugin<Project> {

    void apply(Project project) {
        if (BuildUtils.getServerProjectPath(project.gradle).equals(project.getPath())) {
            project.apply plugin: 'org.labkey.build.serverDeploy'

            project.dependencies {
                tomcatJars  "org.labkey.build:tomcat-libs:${project.labkeyVersion}"
            }
        }
    }
}
