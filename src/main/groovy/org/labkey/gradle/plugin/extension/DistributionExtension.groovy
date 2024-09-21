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

import java.nio.file.Paths

class DistributionExtension
{
    // the directory in which the file 'distribution' is placed, which contains the name of the distribution
    // (used for mothership reporting and troubleshooting)
    public static final String DIST_FILE_DIR = "labkeywebapp/WEB-INF/classes"
    public static final String DIST_FILE_NAME = "distribution"
    public static final String VERSION_FILE_NAME = "VERSION"
    public static final String TAR_ARCHIVE_EXTENSION = "tar.gz"

    String dir = "${project.rootProject.projectDir}/dist"
    String artifactId
    String description

    private Project project

    DistributionExtension(Project project)
    {
        this.project = project
    }

    static File getDistributionFile(Project project) {
        File distDir = new File(project.rootDir, "dist")
        if (project.hasProperty("distDir"))
        {
            if (Paths.get((String) project.property('distDir')).isAbsolute())
                distDir = new File((String) project.property('distDir'));
            else
                distDir = new File(project.rootDir, (String) project.property("distDir"))
        }
        if (!distDir.exists())
            throw new GradleException("Distribution directory ${distDir} not found")
        File[] distFiles = distDir.listFiles(new FilenameFilter() {
            @Override
            boolean accept(File dir, String name) {
                return name.endsWith(TAR_ARCHIVE_EXTENSION)
            }
        })
        if (distFiles == null || distFiles.length == 0)
            throw new GradleException("No distribution found in directory ${distDir} with extension ${TAR_ARCHIVE_EXTENSION}")
        else if (distFiles.length > 1)
            throw new GradleException("${distDir} contains ${distFiles.length} files with extension ${TAR_ARCHIVE_EXTENSION}. Only one is allowed.")
        return distFiles[0]
    }
}
