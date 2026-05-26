/*
 * Copyright (c) 2017-2026 LabKey Corporation
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

import java.nio.file.Paths

class DistributionExtension
{
    // directory that holds the 'distribution.properties' file
    public static final String DIST_FILE_DIR = "labkeywebapp/WEB-INF/classes"
    public static final String DIST_PROPERTIES_FILE_NAME = "distribution.properties"
    public static final String TAR_ARCHIVE_EXTENSION = "tar.gz"

    String dir = "${project.rootProject.projectDir}/dist"

    private Project project

    DistributionExtension(Project project)
    {
        this.project = project
    }

    static File getDistributionFile(Project project, String distDirName) {
        File distDir
        if (Paths.get(distDirName).isAbsolute())
            distDir = new File((String) project.property('distDir'))
        else
            distDir = new File(project.rootDir, distDirName)
        if (distDir.exists()) {
            File[] distFiles = distDir.listFiles(new FilenameFilter() {
                @Override
                boolean accept(File dir, String name)
                {
                    return name.endsWith(TAR_ARCHIVE_EXTENSION)
                }
            })
            if (distFiles == null || distFiles.length == 0 || distFiles.length > 1)
                return null

            return distFiles[0]
        }
        return null
    }
}
