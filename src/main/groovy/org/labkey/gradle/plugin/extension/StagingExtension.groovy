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
        dir = "${project.rootProject.buildDir}/${STAGING_DIR}"
        webappClassesDir = "${project.rootProject.buildDir}/${STAGING_WEBINF_DIR}/classes"
        jspDir = "${project.rootProject.buildDir}/${STAGING_WEBINF_DIR}/jsp"
        webInfDir = "${project.rootProject.buildDir}/${STAGING_WEBINF_DIR}"
        webappDir = "${project.rootProject.buildDir}/${STAGING_WEBAPP_DIR}"
        modulesDir = "${project.rootProject.buildDir}/${STAGING_MODULES_DIR}"
        tomcatLibDir = "${dir}/tomcat-lib" // Note: Keep this path in sync with AdminController.getTomcatJars()
        pipelineLibDir = "${dir}/pipelineLib"
    }
}
