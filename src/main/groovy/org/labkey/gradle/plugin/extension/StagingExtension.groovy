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
import org.labkey.gradle.util.BuildUtils

class StagingExtension
{
    public static final String STAGING_DIR = "staging"
    public static final String STAGING_MODULES_DIR = "${STAGING_DIR}/modules/"
    public static final String STAGING_WEBAPP_DIR = "${STAGING_DIR}/labkeyWebapp"
    public static final String STAGING_WEBINF_DIR = "${STAGING_WEBAPP_DIR}/WEB-INF/"

    String dir
    String webappClassesDir
    String jspDir
    String webInfDir
    String webappDir
    String modulesDir
    String tomcatLibDir
    String pipelineLibDir

    void setDirectories(Project project)
    {
        String buildDirPath = BuildUtils.getRootBuildDirPath(project)
        dir = "${buildDirPath}/${STAGING_DIR}"
        webappClassesDir = "${buildDirPath}/${STAGING_WEBINF_DIR}/classes"
        jspDir = "${buildDirPath}/${STAGING_WEBINF_DIR}/jsp"
        webInfDir = "${buildDirPath}/${STAGING_WEBINF_DIR}"
        webappDir = "${buildDirPath}/${STAGING_WEBAPP_DIR}"
        modulesDir = "${buildDirPath}/${STAGING_MODULES_DIR}"
        tomcatLibDir = "${dir}/tomcat-lib" // Note: Keep this path in sync with AdminController.getTomcatJars()
        pipelineLibDir = "${dir}/pipelineLib"
    }
}
