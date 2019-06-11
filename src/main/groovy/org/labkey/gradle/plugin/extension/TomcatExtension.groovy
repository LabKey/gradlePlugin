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

/**
 * Created by susanh on 4/23/17.
 */
class TomcatExtension
{
    private final Project project

    String catalinaHome
    String tomcatConfDir
    String assertionFlag = "-ea" // set to -da to disable assertions and -ea to enable assertions
    String maxMemory = "1G"
    boolean recompileJsp = true
    String trustStore = ""
    String trustStorePassword = ""
    String catalinaOpts = ""
    String debugPort = null // this is used for TeamCity catalina options

    TomcatExtension(Project project)
    {
        this.project = project
        setCatalinaDirs(project)
    }

    String getCatalinaHome()
    {
        validateCatalinaHome()
        return catalinaHome
    }

    String getTomcatConfDir()
    {
        validateCatalinaHome()
        return tomcatConfDir
    }

    private void validateCatalinaHome()
    {
        String errorMsg = ""
        if (catalinaHome == null || catalinaHome.isEmpty())
        {
            errorMsg = "Tomcat home directory not set"
        }
        else if (!project.file(catalinaHome).exists())
        {
            errorMsg = "Specified Tomcat home directory [${project.file(catalinaHome).getAbsolutePath()}] does not exist"
        }
        else if (!new File(project.file(catalinaHome), "conf/server.xml").exists())
        {
            errorMsg = "Specified Tomcat home directory [${project.file(catalinaHome).getAbsolutePath()}] does not appear to be a tomcat installation"
        }
        if (!errorMsg.isEmpty())
        {
            throw new GradleException("${errorMsg}. Please specify using the environment variable CATALINA_HOME. " +
                    "You may also set the value of the 'tomcat.home' system property using either " +
                    "systemProp.tomcat.home=<tomcat home directory> in a gradle.properties file or " +
                    "-Dtomcat.home=<tomcat home directory> on command line. Note that CATALINA_HOME is not, generally, " +
                    "visible from within IntelliJ IDEA")
        }
    }

    private void setCatalinaDirs(Project project)
    {
        if (System.getenv("CATALINA_HOME") != null)
        {
            this.catalinaHome = System.getenv("CATALINA_HOME")
        }
        else if (project.hasProperty("tomcatDir"))
        {
            this.catalinaHome = project.tomcatDir
        }
        else if (project.ext.hasProperty("tomcatDir"))
        {
            this.catalinaHome = project.ext.tomcatDir
        }
        else
        {
            this.catalinaHome = TeamCityExtension.getTeamCityProperty(project, "tomcat.home", null)
        }

        if (project.ext.hasProperty("tomcatConfDir"))
        {
            this.tomcatConfDir = project.ext.tomcatConfDir
        }
        else if (this.catalinaHome != null)
        {
            this.tomcatConfDir = "${this.catalinaHome}/conf/Catalina/localhost"
        }
        else
        {
            this.tomcatConfDir = null
        }
    }
}
