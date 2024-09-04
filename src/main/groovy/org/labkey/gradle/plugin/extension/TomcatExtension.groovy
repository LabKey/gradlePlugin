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

class TomcatExtension
{
    private final Project project

    String catalinaHome
    String tomcatConfDir
    String assertionFlag = "-ea" // set to -da to disable assertions and -ea to enable assertions
    String maxMemory = "2G"
    boolean disableRecompileJsp = false
    boolean ignoreModuleSource = false
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
        return catalinaHome
    }

    String getTomcatConfDir()
    {
        return tomcatConfDir
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
