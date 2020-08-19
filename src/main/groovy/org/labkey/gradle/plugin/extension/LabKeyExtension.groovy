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
package org.labkey.gradle.plugin.extension

import org.gradle.api.Project

/**
 * Created by susanh on 4/23/17.
 */
class LabKeyExtension
{
    private static final String DEPLOY_MODE_PROPERTY = "deployMode"
    public static final String LABKEY_GROUP = "org.labkey"
    public static final String MODULE_GROUP_SUFFIX = ".module"
    public static final String API_GROUP_SUFFIX = ".api"
    public static final String LABKEY_MODULE_GROUP = LABKEY_GROUP + MODULE_GROUP_SUFFIX
    public static final String LABKEY_API_GROUP = LABKEY_GROUP + API_GROUP_SUFFIX
    public static final String LABKEY_TEST_GROUP = LABKEY_GROUP + ".test"
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
    String ext3Dir = "ext-3.4.1"
    String ext4Dir = "ext-4.2.1"

    static String getDeployModeName(Project project)
    {
        if (isDevMode(project))
            return DeployMode.dev.getDisplayName()
        else
            return DeployMode.valueOf(project.property(DEPLOY_MODE_PROPERTY).toString().toLowerCase()).getDisplayName()
    }

    static boolean isDevMode(Project project)
    {
        return !project.hasProperty(DEPLOY_MODE_PROPERTY) ||
                (project.hasProperty(DEPLOY_MODE_PROPERTY) && DeployMode.dev.toString().equalsIgnoreCase((String) project.property(DEPLOY_MODE_PROPERTY)))
    }

    void setDirectories(Project project)
    {
        explodedModuleDir = "${project.buildDir}/explodedModule"
        explodedModuleWebDir = "${explodedModuleDir}/web"
        explodedModuleConfigDir = "${explodedModuleDir}/config"
        explodedModuleLibDir = "${explodedModuleDir}/lib"
        srcGenDir = "${project.buildDir}/gensrc"

        externalDir = "${project.rootDir}/external"
    }

    private static Properties getBasePomProperties(String artifactPrefix, String description, Project project)
    {
        Properties pomProperties = new Properties()
        pomProperties.put("ArtifactId", artifactPrefix)
        pomProperties.put("Organization", "LabKey")
        pomProperties.put("OrganizationURL", "http://www.labkey.org")
        pomProperties.put("License", "The Apache Software License, Version 2.0")
        pomProperties.put("LicenseURL", "http://www.apache.org/licenses/LICENSE-2.0.txt")
        ModuleExtension modExtension = new ModuleExtension(project)
        pomProperties.putAll(modExtension.getModProperties())

        if (description != null)
            pomProperties.put("Description", description)
        return pomProperties
    }

    static Properties getApiPomProperties(String artifactPrefix, String description, Project project)
    {
        Properties pomProperties = getBasePomProperties(artifactPrefix, description, project)
        pomProperties.put("groupId", project.group + ".api")
        pomProperties.setProperty("artifactCategory", "apiLib")
        pomProperties.setProperty("scope", "compile")
        return pomProperties
    }

    static Properties getApiPomProperties(Project project)
    {
        return getApiPomProperties(project.name, project.description, project)
    }

    static Properties getModulePomProperties(Project project)
    {
        Properties pomProperties = getBasePomProperties(project.name, project.description, project)
        pomProperties.put("groupId", project.group + ".module")
        pomProperties.setProperty("artifactCategory", "modules")
        pomProperties.setProperty("type", "module")
        pomProperties.setProperty("scope", "runtime")
        return pomProperties
    }
}
