package org.labkey.gradle.task

import org.apache.commons.lang3.SystemUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy

class DeployAppBase extends DefaultTask {

    private File _externalDir = new File((String) project.labkey.externalDir)

    protected void deployPlatformBinaries(File deployBinDir)
    {
        deployBinDir.mkdirs()

        if (project.configurations.findByName("binaries") != null)
        {
            project.logger.debug("Copying from binaries configuration to ${deployBinDir}")
            project.copy({
                CopySpec copy ->
                    copy.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
                    copy.from(project.configurations.binaries.collect { project.zipTree(it) })
                    copy.into deployBinDir.path
            })
            project.logger.debug("Contents of ${deployBinDir}\n" + deployBinDir.listFiles())
        }
        // For TC builds, we deposit the artifacts of the Linux TPP Tools and Windows Proteomics Tools into
        // the external directory, so we want to copy those over as well.
        // TODO: package the output of these builds into the Artifactory artifact to simplify
        if (project.file(_externalDir).exists()) {
            project.logger.info("Copying from ${_externalDir} to ${project.serverDeploy.binDir}")
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
        File parentDir = new File(_externalDir, "${osDirectory}")
        if (parentDir.exists())
        {
            List<File> subDirs = parentDir.listFiles new FileFilter() {
                @Override
                boolean accept(File pathname) {
                    return pathname.isDirectory()
                }
            }
            for (File dir : subDirs) {
                project.copy { CopySpec copy ->
                    copy.from dir
                    copy.into deployBinDir.getPath()
                    copy.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
                }
            }
        }
    }

    private void deployBinariesViaAntCopy(String osDirectory, File deployBinDir)
    {
        def fromDir = "${_externalDir}/${osDirectory}"
        if (project.file(fromDir).exists())
        {
            ant.copy(
                    todir: deployBinDir.getPath(),
                    preserveLastModified: true
            )
                    {
                        ant.cutdirsmapper(dirs: 1)
                        fileset(dir: fromDir)
                                {
                                    exclude(name: "**.*")
                                }
                    }
        }
    }


    protected void deployNlpEngine(File deployBinDir)
    {

        File nlpSource = new File(_externalDir, "nlp")
        if (nlpSource.exists())
        {
            File nlpDir = new File(deployBinDir, "nlp")
            nlpDir.mkdirs()
            ant.copy(
                    toDir: nlpDir,
                    preserveLastModified: true
            )
                    {
                        fileset(dir: nlpSource)
                                {
                                    exclude(name: "**/*.py?")
                                }
                    }
        }
    }

}
