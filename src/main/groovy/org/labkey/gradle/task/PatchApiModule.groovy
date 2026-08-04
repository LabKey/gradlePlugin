/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.gradle.task

import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.util.PatternFilterable

import javax.inject.Inject

/**
 * Produces a copy of a module archive in which the open-source ExtJS libraries are replaced by their
 * commercial-license counterparts. The archives to combine are provided as input file collections,
 * which are not resolved until the task executes.
 */
@CacheableTask
abstract class PatchApiModule extends Jar
{
    @Inject abstract ArchiveOperations getArchiveOps()

    /** Archives containing the commercial ExtJS 3 libraries, in a top-level ext-3.x.y directory */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    abstract ConfigurableFileCollection getExtJs3Archives()

    /** Archives containing the commercial ExtJS 4 libraries, in a top-level ext-4.x.y directory */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    abstract ConfigurableFileCollection getExtJs4Archives()

    /** The module archives to be patched with the commercial libraries */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    abstract ConfigurableFileCollection getModuleArchives()

    PatchApiModule()
    {
        // first include the ext-3.4.1 and ext-4.2.1 directories from the extjs configuration artifacts
        into('web', { CopySpec spec ->
            spec.from({ extJs3Archives.files.collect { archiveOps.zipTree(it) } })
        })
        into('web', { CopySpec spec ->
            spec.from({ extJs4Archives.files.collect { archiveOps.zipTree(it) } })
        })
        // include the original module file ...
        from({
            moduleArchives.files.collect {
                archiveOps.zipTree(it).matching({ PatternFilterable pattern ->
                    // DuplicatesStrategy.EXCLUDE doesn't seem to work in some environments
                    pattern.exclude('web/ext-*/**')
                })
            }
        })
        // ... but don't use the ext directories that come from that file
        setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
    }
}