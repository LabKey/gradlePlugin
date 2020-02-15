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
package org.labkey.gradle.util

import org.gradle.api.Project

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/**
 * Finder for module directories that match a particular pattern.
 */
class ModuleFinder extends SimpleFileVisitor<Path>
{
    private final PathMatcher matcher
    private List<String> modulePaths = []
    private File rootDir
    private List<String> excluded = []

    private static List<String> NON_MODULE_DIRS = ["build", "buildSrc", "dist", "external", "intellijBuild", "sampledata", "schemas", "test_secure", "tools", "vagrant", "webapps"]

    ModuleFinder(File rootDir, String pattern)
    {
        this.rootDir = rootDir
        matcher = FileSystems.getDefault().getPathMatcher("glob:"+ pattern)
    }

    ModuleFinder(File rootDir, String pattern, List<String> excluded)
    {
        this(rootDir, pattern)
        this.excluded = excluded
    }

    // Compares the glob pattern against the directory name.
    void find(Path directory)
    {
        if (directory != null && matcher.matches(directory)) {
            modulePaths += BuildUtils.convertDirToPath(rootDir, directory.toFile())
        }
    }


    @Override
    FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        if (dir.getFileName().toString().startsWith(".") ||
                excluded.contains(dir.getFileName().toString()) ||
                NON_MODULE_DIRS.contains(dir.getFileName().toString()))
        {
            return FileVisitResult.SKIP_SUBTREE
        }
        else
        {
            find(dir)
            return FileVisitResult.CONTINUE
        }
    }

    // This method is called before all plugins are applied, so we cannot use a check for the Distribution Plugin here.
    static boolean isDistributionProject(Project p)
    {
        return p.path.toLowerCase().contains(":distributions")
    }

    static boolean isModuleContainer(Project p)
    {
        return (p.findProperty("moduleContainer") && p.path.equalsIgnoreCase(p.property("moduleContainer")))
    }

    static boolean isPotentialModule(Project p)
    {
        return !p.name.startsWith(".") &&
                !p.name.toLowerCase().equals("webpack") &&
                !p.name.toLowerCase().equals("test") &&
                !isDistributionProject(p) &&
                !isModuleContainer(p)
    }
}
