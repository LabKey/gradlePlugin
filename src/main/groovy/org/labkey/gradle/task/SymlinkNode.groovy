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

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Creates symbolic links to the npm and node directories of the node bin project so they can be used in the PATH
 * environment variable. Not applicable on Windows, where creating symbolic links requires elevated permissions.
 */
@UntrackedTask(because="Symbolic links are not tracked as task outputs")
abstract class SymlinkNode extends DefaultTask
{
    private static final String PACKAGE_MANAGER = "npm"

    /** The directory the symbolic links are created in */
    @Internal
    abstract DirectoryProperty getLinkContainerDir()

    /** The directory containing the versioned npm directory the npm link points to */
    @Internal
    abstract DirectoryProperty getNpmTargetDir()

    /** The directory containing the versioned node directory the node link points to */
    @Internal
    abstract DirectoryProperty getNodeTargetDir()

    @Internal
    abstract Property<String> getNpmVersion()

    @Internal
    abstract Property<String> getNodeVersion()

    @TaskAction
    void action()
    {
        if (!linkContainerDir.isPresent() || !npmVersion.isPresent() || !npmTargetDir.isPresent())
        {
            logger.info("Symbolic links not created because the npm properties or the node bin project were not found.")
            return
        }

        File linkContainer = linkContainerDir.get().asFile
        linkContainer.mkdirs()

        Path pmLinkPath = Paths.get("${linkContainer.getPath()}/${PACKAGE_MANAGER}")
        String pmDirName = "${PACKAGE_MANAGER}-v${npmVersion.get()}"
        Path pmTargetPath = Paths.get(new File(npmTargetDir.get().asFile, pmDirName).getPath())

        if (!Files.isSymbolicLink(pmLinkPath) || !Files.readSymbolicLink(pmLinkPath).getFileName().toString().equals(pmDirName))
        {
            // if the symbolic link exists, we want to replace it
            if (Files.isSymbolicLink(pmLinkPath))
                Files.delete(pmLinkPath)

            Files.createSymbolicLink(pmLinkPath, pmTargetPath)
        }

        String nodeFilePrefix = "node-v${nodeVersion.get()}-"
        Path nodeLinkPath = Paths.get("${linkContainer.getPath()}/node")
        if (!Files.isSymbolicLink(nodeLinkPath) || !Files.readSymbolicLink(nodeLinkPath).getFileName().toString().startsWith(nodeFilePrefix))
        {
            if (!nodeTargetDir.isPresent())
            {
                logger.warn("No node work directory found. Symbolic link in ${linkContainer.getPath()}/node not created.")
                return
            }
            File nodeDir = nodeTargetDir.get().asFile
            File[] nodeFiles = nodeDir.listFiles({ File file -> file.name.startsWith(nodeFilePrefix) } as FileFilter)
            if (nodeFiles != null && nodeFiles.length > 0)
            {
                // if the symbolic link exists, we want to replace it
                if (Files.isSymbolicLink(nodeLinkPath))
                    Files.delete(nodeLinkPath)

                Files.createSymbolicLink(nodeLinkPath, nodeFiles[0].toPath())
            }
            else
                logger.warn("No file found with prefix ${nodeDir.path}/${nodeFilePrefix}.  Symbolic link in ${linkContainer.getPath()}/node not created.")
        }
    }
}
