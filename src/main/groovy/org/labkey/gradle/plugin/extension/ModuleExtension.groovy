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
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.PropertiesUtils

import java.text.SimpleDateFormat

/**
 * Created by susanh on 4/23/17.
 */
class ModuleExtension
{
    private static final String ENLISTMENT_PROPERTIES = "enlistment.properties"
    public static final String MODULE_PROPERTIES_FILE = "module.properties"
    public static final String MODULE_DEPENDENCIES_PROPERTY = "ModuleDependencies"
    private Properties modProperties
    private Project project

    ModuleExtension(Project project)
    {
        this.project = project
        setModuleProperties(project)
    }

    Project getProject()
    {
        return project
    }

    Properties getModProperties()
    {
        return modProperties
    }

    String getPropertyValue(String propertyName, String defaultValue)
    {
        String value = modProperties.getProperty(propertyName)
        return value == null ? defaultValue : value

    }

    String getPropertyValue(String propertyName)
    {
        return getPropertyValue(propertyName, null)
    }

    Object get(String propertyName)
    {
        return modProperties.get(propertyName)
    }

    void setPropertyValue(String propertyName, String value)
    {
        modProperties.setProperty(propertyName, value)
    }

    void setModuleProperties(Project project)
    {
        this.modProperties = new Properties()
        File propertiesFile = project.file(MODULE_PROPERTIES_FILE)
        if (propertiesFile.exists())
            PropertiesUtils.readProperties(propertiesFile, this.modProperties)
        else
            project.logger.info("${project.path} - no ${MODULE_PROPERTIES_FILE} found")

        setBuildInfoProperties()
        setModuleInfoProperties()
        setVcsProperties()
        setEnlistmentId()
    }

    private void setVcsProperties()
    {
        modProperties.putAll(BuildUtils.getStandardVCSProperties(project))
    }

    private setEnlistmentId()
    {
        File enlistmentFile = new File(project.getRootProject().getProjectDir(), ENLISTMENT_PROPERTIES)
        Properties enlistmentProperties = new Properties()
        if (!enlistmentFile.exists())
        {
            UUID id = UUID.randomUUID()
            enlistmentProperties.setProperty("enlistment.id", id.toString())
            enlistmentProperties.store(new FileWriter(enlistmentFile), SimpleDateFormat.getDateTimeInstance().format(new Date()))
        }
        else
        {
            PropertiesUtils.readProperties(enlistmentFile, enlistmentProperties)
        }
        modProperties.setProperty("EnlistmentId", enlistmentProperties.getProperty("enlistment.id"))
    }

    private void setBuildInfoProperties()
    {
        modProperties.setProperty("RequiredServerVersion", "0.0")
        if (modProperties.getProperty("BuildType") == null)
            modProperties.setProperty("BuildType", LabKeyExtension.getDeployModeName(project))
        modProperties.setProperty("BuildUser", System.getProperty("user.name"))
        modProperties.setProperty("BuildOS", System.getProperty("os.name"))
        modProperties.setProperty("BuildTime", SimpleDateFormat.getDateTimeInstance().format(new Date()))
        modProperties.setProperty("BuildPath", project.buildDir.getAbsolutePath())
        modProperties.setProperty("SourcePath", project.projectDir.getAbsolutePath())
        modProperties.setProperty("ResourcePath", "") // TODO  _project.getResources().... ???
        modProperties.setProperty("ReleaseVersion", (String) project.getProperty("labkeyVersion"))
        if (modProperties.getProperty("ManageVersion") == null)
        {
            modProperties.setProperty("ManageVersion", "true")
        }
        if (modProperties.getProperty("SchemaVersion") == null)
        {
            if (modProperties.getProperty("Version") == null)
                modProperties.setProperty("SchemaVersion", "")  // Spring binds this as setSchemaVersion(null), which is what we want
            else
                modProperties.setProperty("SchemaVersion", modProperties.getProperty("Version"))  // For backward compatibility with old modules TODO: Remove
        }
    }

    private void setModuleInfoProperties()
    {
        if (modProperties.getProperty("Name") == null)
            modProperties.setProperty("Name", project.name)
        if (modProperties.getProperty("ModuleClass") == null)
            modProperties.setProperty("ModuleClass", "org.labkey.api.module.SimpleModule")
    }
}
