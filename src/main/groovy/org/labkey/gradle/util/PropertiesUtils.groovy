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

import org.apache.commons.lang3.StringEscapeUtils
import org.gradle.api.Project

import java.util.regex.Matcher
import java.util.regex.Pattern

class PropertiesUtils
{
    public static final Pattern PROPERTY_PATTERN = Pattern.compile("@@([^@]+)@@")
    private static final Pattern VALUE_PATTERN = Pattern.compile("(\\\$\\{\\w*\\})")

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

    static String replaceProps(String line, Properties props, Boolean xmlEncode = false)
    {
        Matcher matcher = PROPERTY_PATTERN.matcher(line)
        while (matcher.find())
        {
            String propName = matcher.group(1)
            if (props.containsKey(propName))
            {
                String val = props.get(propName).toString()
                if (val != null)
                {
                    if (xmlEncode)
                        val = StringEscapeUtils.escapeXml10(val)
                    line = line.replace("@@" + propName + "@@", val)
                }
            }
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
}