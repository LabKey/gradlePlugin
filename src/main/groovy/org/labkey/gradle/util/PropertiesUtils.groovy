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

import org.gradle.api.Project
import org.slf4j.Logger

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

    static Properties readFileProperties(File propFile)
    {
        Properties props = new Properties()
        if (propFile.exists())
            props.load(new FileInputStream(propFile))
        return props
    }

    static String parseCompositeProp(String projectPath, Properties props, String prop, Logger logger)
    {
        if (props == null)
            logger.error("${projectPath} Properties is null")
        else if (prop == null)
            logger.error("${projectPath} Property is null; no parsing possible")
        else
        {
            Matcher valMatcher = VALUE_PATTERN.matcher(prop)
            while (valMatcher.find())
            {
                String p = valMatcher.group(1).replace("\${", "").replace("}", "")
                if (props.getProperty(p) != null)
                    prop = prop.replace(valMatcher.group(1), (String) (props.getProperty(p)))
                else
                    logger.error("Unable to find value for ${p} in ${props}")
            }
        }
        return prop
    }

    static String replacePropInLine(String line, String propName, Object val)
    {
        if (val != null)
        {
            String stringVal = val.toString()
            return line.replace("@@" + propName + "@@", stringVal)
        }
        return line
    }

    static String replaceProps(String line, Properties props)
    {
        Matcher matcher = PROPERTY_PATTERN.matcher(line)
        while (matcher.find())
        {
            String propName = matcher.group(1)
            if (props.containsKey(propName))
                line = replacePropInLine(line, propName, props.get(propName))
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

    static Properties getApplicationProperties(File propertiesFile)
    {
        def applicationProperties = new Properties()
        readProperties(propertiesFile, applicationProperties)
        return applicationProperties
    }
}
