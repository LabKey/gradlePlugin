package org.labkey.gradle.task

import org.apache.commons.lang3.SystemUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault
import org.labkey.gradle.util.BuildUtils

import javax.inject.Inject

@DisableCachingByDefault(because="Outputs are in the deploy directory")
abstract class DeployAppBase extends SetUpProperties {

    @Inject abstract FileSystemOperations getFs()

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getBinaries()

    @OutputDirectory
    final abstract DirectoryProperty _externalDir = BuildUtils.getRootBuildDirectoryProperty(project, "external")

    protected void deployPlatformBinaries(File deployBinDir)
    {
        deployBinDir.mkdirs()

        if (getBinaries() != null && !getBinaries().isEmpty())
        {
            this.logger.debug("Copying from binaries configuration to ${deployBinDir}")
            fs.copy({
                CopySpec copy ->
                    copy.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
                    copy.from(getBinaries().collect { project.zipTree(it) })
                    copy.into deployBinDir.path
            })
            this.logger.debug("Contents of ${deployBinDir}\n" + deployBinDir.listFiles())
        }
        // For TC builds, we deposit the artifacts of the Linux TPP Tools and Windows Proteomics Tools into
        // the external directory, so we want to copy those over as well.
        // TODO: package the output of these builds into the Artifactory artifact to simplify
        if (_externalDir.get().asFile.exists()) {
            this.logger.info("Copying from ${_externalDir.get()} to ${deployBinDir}")
            if (SystemUtils.IS_OS_MAC)
                deployBinariesViaProjectCopy("osx", deployBinDir)
            else if (SystemUtils.IS_OS_LINUX)
                deployBinariesViaProjectCopy("linux", deployBinDir)
            else if (SystemUtils.IS_OS_WINDOWS)
                deployBinariesViaAntCopy("windows", deployBinDir)
        }
    }

    // Use this method to preserve file permissions, since ant.copy does not, but this does not preserve last modified times
    private void deployBinariesViaProjectCopy(String osDirectory, File deployBinDir)
    {
        File parentDir = new File(_externalDir.get().asFile, "${osDirectory}")
        if (parentDir.exists())
        {
            List<File> subDirs = parentDir.listFiles new FileFilter() {
                @Override
                boolean accept(File pathname) {
                    return pathname.isDirectory()
                }
            }
            for (File dir : subDirs) {
                fs.copy { CopySpec copy ->
                    copy.from dir
                    copy.into deployBinDir.getPath()
                    copy.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
                }
            }
        }
    }

    private void deployBinariesViaAntCopy(String osDirectory, File deployBinDir)
    {
        File fromDir = _externalDir.get().file(osDirectory).asFile
        if (fromDir.exists())
        {
            ant.copy(
                    todir: deployBinDir.getPath(),
                    preserveLastModified: true
            )
                    {
                        ant.cutdirsmapper(dirs: 1)
                        fileset(dir: fromDir.path)
                                {
                                    exclude(name: "**.*")
                                }
                    }
        }
    }
}
