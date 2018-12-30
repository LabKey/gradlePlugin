/*
 * Copyright (c) 2016-2018 LabKey Corporation
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
package org.labkey.gradle.plugin

import org.gradle.api.Project
import org.labkey.gradle.util.BuildUtils

/**
 * This class is used for building a LabKey module (one that typically resides in a *modules
 * directory).  It defines tasks for building the jar files (<module>_api.jar, <module>_jsp.jar, <module>.jar, <module>_schemas.jar)
 * as well as tasks for copying resources to the module's build directory.  This differs from java module
 * in that it allows for a separate api jar and a schemas jar that the compileJava tasks depend on.
 */
class Module extends JavaModule
{
    @Override
    void apply(Project project)
    {
        super.apply(project)

        if (!AntBuild.isApplicable(project))
        {
            addDependencies(project)
        }
    }

    private void addDependencies(Project project)
    {
        project.dependencies
                {
                    // This is only required for the api module to compile, but we exclude the declaration of dependencies from
                    // the api module's pom file because we cannot specify a version, since we rely on the local tomcat version.
                    // Therefore, when relying on the api jar file not built from source, we require this extra definition;
                    // it will not find the tomcat jar files without this.
                    implementation project.fileTree(dir: "${project.ext.tomcatDir}/lib", includes: ['*.jar'], excludes: ['servlet-api.jar', 'mail.jar'])

                    BuildUtils.addLabKeyDependency(project: project, config: "implementation", depProjectPath: BuildUtils.getProjectPath(project.gradle, "internalProjectPath", ":server:internal"), depVersion: project.labkeyVersion)
                    BuildUtils.addLabKeyDependency(project: project, config: "implementation", depProjectPath: BuildUtils.getProjectPath(project.gradle, "remoteapiProjectPath", ":remoteapi:java"), depVersion: project.labkeyVersion)
                    implementation 'org.apache.tomcat:jsp-api'
                    implementation 'org.apache.tomcat:jasper'
                    if (XmlBeans.isApplicable(project))
                        implementation project.files(project.tasks.schemasJar)
                    if (Api.isApplicable(project))
                        implementation project.files(project.tasks.apiJar)
                }
    }
}

