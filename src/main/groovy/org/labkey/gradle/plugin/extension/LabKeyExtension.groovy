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
package org.labkey.gradle.plugin.extension

import org.gradle.api.Project

/**
 * Created by susanh on 4/23/17.
 */
class LabKeyExtension
{
    private static final String DEPLOY_MODE_PROPERTY = "deployMode"
    private static enum DeployMode {

        dev("Development"),
        prod("Production")

        private String _displayName;

        private DeployMode(String displayName)
        {
            _displayName = displayName;
        }

        String getDisplayName()
        {
            return _displayName
        }
    }

    Boolean skipBuild = false // set this to true in an individual module's build.gradle file to skip building

    String explodedModuleDir
    String explodedModuleWebDir
    String explodedModuleConfigDir
    String explodedModuleLibDir

    String srcGenDir
    String externalDir
    String externalLibDir
    String ext3Dir = "ext-3.4.1"
    String ext4Dir = "ext-4.2.1"

    String server = "http://localhost"
    String port = "8080"
    String contextPath = "/labkey"

    /**
     * @param project the project in question
     * @return true if the project given is one of the modules whose jar file needs to be in the labkeyWebapp/WEB-INF/lib directory
     * at startup
     */
    static Boolean isBootstrapModule(Project project)
    {
        return [":schemas", ":remoteapi:java"].contains(project.path)
    }

    static String getDeployModeName(Project project)
    {
        if (!project.hasProperty(DEPLOY_MODE_PROPERTY))
            return LabKeyExtension.DeployMode.dev.getDisplayName()
        else
            return LabKeyExtension.DeployMode.valueOf(project.property(DEPLOY_MODE_PROPERTY).toString().toLowerCase()).getDisplayName()
    }

    static boolean isDevMode(Project project)
    {
        return project.hasProperty(DEPLOY_MODE_PROPERTY) && LabKeyExtension.DeployMode.dev.toString().equalsIgnoreCase((String) project.property(DEPLOY_MODE_PROPERTY))
    }

    void setDirectories(Project project)
    {
        explodedModuleDir = "${project.buildDir}/explodedModule"
        explodedModuleWebDir = "${explodedModuleDir}/web"
        explodedModuleConfigDir = "${explodedModuleDir}/config"
        explodedModuleLibDir = "${explodedModuleDir}/lib"
        srcGenDir = "${project.buildDir}/gensrc"

        externalDir = "${project.rootDir}/external"
        externalLibDir = "${externalDir}/lib"
    }

    static Properties getBasePomProperties(String artifactPrefix, String description)
    {
        Properties pomProperties = new Properties()
        pomProperties.put("ArtifactId", artifactPrefix)
        pomProperties.put("Organization", "LabKey")
        pomProperties.put("OrganizationURL", "http://www.labkey.org")
        if (description != null)
            pomProperties.put("Description", description)
        pomProperties.put("License", "The Apache Software License, Version 2.0")
        pomProperties.put("LicenseURL", "http://www.apache.org/licenses/LICENSE-2.0.txt")
        return pomProperties
    }
}
