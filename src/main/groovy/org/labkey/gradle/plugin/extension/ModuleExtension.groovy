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

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.ExternalDependency
import org.labkey.gradle.util.PropertiesUtils

import java.text.SimpleDateFormat

class ModuleExtension
{
    private static final String ENLISTMENT_PROPERTIES = "enlistment.properties"
    public static final String MODULE_PROPERTIES_FILE = "module.properties"
    private Map<Object, Object> modProperties = new HashMap<>()
    private Project project
    private Map<String, ExternalDependency> externalDependencies = new HashMap<>()

    ModuleExtension(Project project, boolean logDeprecations)
    {
        this.project = project
        setModuleProperties(project, logDeprecations)
    }

    ModuleExtension(Project project)
    {
        this(project, true)
    }

    Project getProject()
    {
        return project
    }

    Map<Object, Object> getModProperties()
    {
        return modProperties
    }

    String getPropertyValue(String propertyName, String defaultValue)
    {
        String value = modProperties.get(propertyName)
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
        modProperties.put(propertyName, value)
    }

    void setModuleProperties(Project project, boolean logDeprecations)
    {
        File propertiesFile = project.file(MODULE_PROPERTIES_FILE)
        if (propertiesFile.exists()) {
            Properties props = new Properties()
            PropertiesUtils.readProperties(propertiesFile, props)

            this.modProperties.putAll(props)
            if (logDeprecations) {
                List<String> deprecationMsgs = []
//          Follow this pattern to deprecate a property in module.properties
                  // Remove check for OldProperty in mmm, yyyy (one year after deprecation)
//                if (this.modProperties.get("OldProperty"))
//                    deprecationMsgs += "The OldProperty property is no longer supported."
                if (!deprecationMsgs.isEmpty())
                    project.logger.quiet("${propertiesFile.absolutePath}: Deprecated or unsupported properties detected.\n\t"
                        + deprecationMsgs.join("\n\t")
                        + "\nRefer to https://www.labkey.org/Documentation/wiki-page.view?name=includeModulePropertiesFile for the current set of supported properties.")
            }

            List<String> unsupportedMsgs = []
            // Remove checks for ModuleDependencies, ConsolidateScripts, and Version properties in Jan, 2026 (one year after designated as unsupported)
            if (this.modProperties.get("ModuleDependencies"))
                unsupportedMsgs += "The 'ModuleDependencies' property is no longer supported."
            if (this.modProperties.get("ConsolidateScripts"))
                unsupportedMsgs += "The 'ConsolidateScripts' property is no longer supported."
            if (this.modProperties.get("Version"))
                unsupportedMsgs += "The 'Version' property is no longer supported."
            if (!unsupportedMsgs.isEmpty())
                throw new GradleException("${propertiesFile.absolutePath}: Unsupported properties detected.\n\t"
                    + unsupportedMsgs.join("\n\t")
                    + "\nRefer to https://www.labkey.org/Documentation/wiki-page.view?name=includeModulePropertiesFile for the current set of supported properties.")
        }
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
        modProperties.put("EnlistmentId", enlistmentProperties.getProperty("enlistment.id"))
    }

    private void setBuildInfoProperties()
    {
        modProperties.put("RequiredServerVersion", "0.0")
        if (modProperties.get("BuildType") == null)
            modProperties.put("BuildType", LabKeyExtension.getDeployModeName(project))
        modProperties.put("BuildUser", System.getProperty("user.name"))
        modProperties.put("BuildOS", System.getProperty("os.name"))
        modProperties.put("BuildTime", SimpleDateFormat.getDateTimeInstance().format(new Date()))
        modProperties.put("BuildPath", BuildUtils.getBuildDir(project).getAbsolutePath())
        modProperties.put("SourcePath", project.projectDir.getAbsolutePath())
        modProperties.put("ResourcePath", "") // TODO  _project.getResources().... ???
        modProperties.put("ReleaseVersion", (String) project.getProperty("labkeyVersion"))
        if (modProperties.get("ManageVersion") == null)
        {
            modProperties.put("ManageVersion", "false") // Issue #47369
        }
        if (modProperties.get("SchemaVersion") == null)
        {
            modProperties.put("SchemaVersion", "")  // Spring binds this as setSchemaVersion(null), which is what we want
        }
    }

    private void setModuleInfoProperties()
    {
        if (modProperties.get("Name") == null)
            modProperties.put("Name", project.name)
        if (modProperties.get("ModuleClass") == null)
            modProperties.put("ModuleClass", "org.labkey.api.module.SimpleModule")
    }

    void addExternalDependency(ExternalDependency dependency)
    {
        if (dependency.coordinates == null)
            throw new GradleException("${project.path}: All external dependencies must have group-name-version coordinates. ${dependency}")
        externalDependencies.put(dependency.coordinates, dependency)
    }

    ExternalDependency getExternalDependency(String coordinates)
    {
        return externalDependencies.get(coordinates)
    }

    Map<String, ExternalDependency> getExternalDependencies()
    {
        return externalDependencies
    }
}
