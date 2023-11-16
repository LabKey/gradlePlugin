/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.gradle.util

import org.apache.commons.text.StringEscapeUtils
import org.gradle.api.Project

import java.util.regex.Matcher
import java.util.regex.Pattern

class PropertiesUtils
{
    public static final Pattern PROPERTY_PATTERN = Pattern.compile("@@([^@]+)@@")
    private static final Pattern VALUE_PATTERN = Pattern.compile("(\\\$\\{\\w*\\})")
    private static final String ENCRYPTION_KEY_PROP_NAME = 'encryptionKey'
    private static final String DEPRECATED_ENCRYPTION_KEY_PROP_NAME = 'masterEncryptionKey'

    static Properties readFileProperties(Project project, String fileName)
    {
        Properties props = new Properties()
        File propFile = project.file(fileName)
        if (propFile.exists())
            props.load(new FileInputStream(propFile))
        return props
    }

    static String parseCompositeProp(Project project, Properties props, String prop)
    {
        if (props == null)
            project.logger.error("${project.path} Properties is null")
        else if (prop == null)
            project.logger.error("${project.path} Property is null; no parsing possible")
        else
        {
            Matcher valMatcher = VALUE_PATTERN.matcher(prop)
            while (valMatcher.find())
            {
                String p = valMatcher.group(1).replace("\${", "").replace("}", "")
                if (props.getProperty(p) != null)
                    prop = prop.replace(valMatcher.group(1), (String) (props.getProperty(p)))
                else
                    project.logger.error("Unable to find value for ${p} in ${props}")
            }
        }
        return prop
    }

    static String replacePropInLine(String line, String propName, Object val, Boolean xmlEncode)
    {
        if (val != null)
        {
            String stringVal = val.toString()
            if (xmlEncode)
                stringVal = StringEscapeUtils.escapeXml10(stringVal)
            return line.replace("@@" + propName + "@@", stringVal)
        }
        return line
    }

    static String replaceProps(String line, Properties props, Boolean xmlEncode = false)
    {
        Matcher matcher = PROPERTY_PATTERN.matcher(line)
        while (matcher.find())
        {
            String propName = matcher.group(1)
            if (props.containsKey(propName))
                line = replacePropInLine(line, propName, props.get(propName), xmlEncode)
            // backward compatibility for labkey.xml having new prop name and config.properties having old one
            // TODO remove these cases once we move to a plugin version that doesn't need to support backward compatibility
            else if (propName.equals(ENCRYPTION_KEY_PROP_NAME) && props.containsKey(DEPRECATED_ENCRYPTION_KEY_PROP_NAME))
                line = replacePropInLine(line, propName, props.get(DEPRECATED_ENCRYPTION_KEY_PROP_NAME), xmlEncode)
            // backward compatibility for labkey.xml having old prop name and config.properties having new one
            else if (propName.equals(DEPRECATED_ENCRYPTION_KEY_PROP_NAME) && props.containsKey(ENCRYPTION_KEY_PROP_NAME))
                line = replacePropInLine(line, propName, props.get(ENCRYPTION_KEY_PROP_NAME), xmlEncode)
        }
        return line
    }

    static void readProperties(File propertiesFile, Properties properties)
    {
        if (propertiesFile.exists())
        {
            FileInputStream is
            try
            {
                is = new FileInputStream(propertiesFile)
                properties.load(is)
            }
            finally
            {
                if (is != null)
                    is.close()
            }
        }
    }

    static Properties getApplicationProperties(Project project)
    {
        def applicationProperties = new Properties()
        readProperties(new File(BuildUtils.getEmbeddedConfigPath(project), "application.properties"), applicationProperties)
        return applicationProperties
    }
}
