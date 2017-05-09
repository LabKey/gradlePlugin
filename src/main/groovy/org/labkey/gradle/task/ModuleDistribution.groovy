package org.labkey.gradle.task

import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.SystemUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecSpec
import org.labkey.gradle.plugin.extension.DistributionExtension
import org.labkey.gradle.plugin.extension.StagingExtension
import org.labkey.gradle.util.PropertiesUtils

import java.nio.file.Files

class ModuleDistribution extends DefaultTask
{
    Boolean includeWindowsInstaller = false
    Boolean includeZipArchive = false
    Boolean includeTarGZArchive = false
    Boolean makeDistribution = true // set to false for just an archive of modules
    String extraFileIdentifier = ""
    Boolean includeMassSpecBinaries = false
    String versionPrefix = null
    String subDirName
    String artifactName

    File distributionDir

    String archivePrefix
    DistributionExtension distExtension

    ModuleDistribution()
    {
        description = "Make a LabKey modules distribution"
        distExtension = project.extensions.findByType(DistributionExtension.class)

        this.dependsOn(project.project(":server").tasks.stageTomcatJars)
        this.dependsOn(project.project(":server").tasks.stageApp)
    }

    File getDistributionDir()
    {
        if (distributionDir == null && subDirName != null)
            distributionDir = project.file("${distExtension.dir}/${subDirName}")
        return distributionDir
    }

    @OutputFiles
    List<File> getDistFiles()
    {
        List<File> distFiles = new ArrayList<>()

        if (includeTarGZArchive)
            distFiles.add(new File(getTarArchivePath()))
        if (includeZipArchive)
            distFiles.add(new File(getZipArchivePath()))
        if (includeWindowsInstaller && SystemUtils.IS_OS_WINDOWS)
            distFiles.add(new File(getDistributionDir(), getWindowsInstallerName()))
        if (makeDistribution)
        {
            distFiles.add(getDistributionFile())
            distFiles.add(getVersionFile())
        }
        return distFiles
    }

    @TaskAction
    void doAction()
    {
        if (makeDistribution)
            createDistributionFiles()
        gatherModules()
        packageRedistributables()

    }

    private String getVersionPrefix()
    {
        if (versionPrefix == null)
            versionPrefix = "Labkey${project.rootProject.installerVersion}${extraFileIdentifier}"
        return versionPrefix
    }

    private String getArchivePrefix()
    {
        if (archivePrefix == null)
            archivePrefix =  "${getVersionPrefix()}-bin"
        return archivePrefix
    }

    File getModulesDir()
    {
        // we use a common directory to save on disk space for TeamCity.  This mimics the behavior of the ant build.
        // (This is just a conjecture about why it continues to run out of space and not be able to copy files from one place to the other).
        return new File("${project.rootProject.buildDir}/distModules")
//        return new File(project.buildDir, "modules")
    }

    private void gatherModules()
    {
        File modulesDir = getModulesDir()
        modulesDir.deleteDir()
        project.copy
        { CopySpec copy ->
            copy.from { project.configurations.distribution }
            copy.into modulesDir
        }
    }

    private void packageRedistributables()
    {
        if (makeDistribution)
        {
            copyLibXml()
            packageInstallers()
        }
        packageArchives()
    }

    private void copyLibXml()
    {
        Properties copyProps = new Properties()
        //The Windows installer only supports Postgres, which it also installs.
        copyProps.put("jdbcURL", "jdbc:postgresql://localhost/labkey")
        copyProps.put("jdbcDriverClassName", "org.postgresql.Driver")

        project.copy
        { CopySpec copy ->
            copy.from("${project.rootProject.projectDir}/webapps")
            copy.include("labkey.xml")
            copy.into(project.buildDir)
            copy.filter({ String line ->
                return PropertiesUtils.replaceProps(line, copyProps, true)
            })
        }
    }

    private void packageInstallers()
    {
        if (includeWindowsInstaller && SystemUtils.IS_OS_WINDOWS) {
//            project.copy {
//                CopySpec copy ->
//                    copy.from(getModulesDir())
//                    copy.into("${project.rootProject.buildDir}/distModules")
//                    copy.include("*.module")
//            }

            project.exec
            { ExecSpec spec ->
                spec.commandLine FilenameUtils.separatorsToSystem("${distExtension.installerSrcDir}/nsis2.46/makensis.exe")
                spec.args = [
                        "/DPRODUCT_VERSION=\"${project.version}\"",
                        "/DPRODUCT_REVISION=\"${project.rootProject.vcsRevision}\"",
                        FilenameUtils.separatorsToSystem("${distExtension.installerSrcDir}/labkey_installer.nsi")
                ]
            }

            project.copy
            { CopySpec copy ->
                copy.from("${project.buildDir}/..") // makensis puts the installer into build/installer without the project name subdirectory
                copy.include("Setup_includeJRE.exe")
                copy.into(getDistributionDir())
                copy.rename("Setup_includeJRE.exe", getWindowsInstallerName())
            }
        }
    }

    private void packageArchives()
    {
        if (includeTarGZArchive)
        {
            tarArchives()
        }
        if (includeZipArchive)
        {
            zipArchives()
        }
    }

    String getWindowsInstallerName()
    {
        return "${getVersionPrefix()}-Setup.exe"
    }

    String getArtifactId()
    {
        return subDirName
    }

    String getArtifactName()
    {
        if (artifactName == null)
        {
            if (makeDistribution)
                artifactName = getArchivePrefix()
            else
                artifactName = getVersionPrefix()
        }
        return artifactName
    }

    private String getTarArchivePath()
    {
        return "${getDistributionDir()}/${getArtifactName()}.tar.gz"
    }

    private String getZipArchivePath()
    {
        return "${getDistributionDir()}/${getArtifactName()}.zip"
    }

    private void tarArchives()
    {
        String archivePrefix = getArchivePrefix()
        if (makeDistribution)
        {
            StagingExtension staging = project.getExtensions().getByType(StagingExtension.class)

            ant.tar(tarfile: getTarArchivePath(),
                    longfile: "gnu",
                    compression: "gzip") {
                tarfileset(dir: staging.webappDir,
                        prefix: "${archivePrefix}/labkeywebapp") {
                    exclude(name: "WEB-INF/classes/distribution")
                }
                tarfileset(dir: getModulesDir(),
                        prefix: "${archivePrefix}/modules") {
                    include(name: "*.module")
                }
                tarfileset(dir: staging.tomcatLibDir, prefix: "${archivePrefix}/tomcat-lib") {
                    // this exclusion is necessary because for some reason when buildFromSource=false,
                    // the tomcat bootstrap jar is included in the staged libraries and the LabKey bootstrap jar is not.
                    // Not sure why.
                    exclude(name: "bootstrap.jar")
                }

                if (includeMassSpecBinaries)
                {
                    tarfileset(dir: "${project.rootProject.projectDir}/external/windows/msinspect",
                            prefix: "${archivePrefix}/bin") {
                        include(name: "**/*.jar")
                        exclude(name: "**/.svn")
                    }
                }

                tarfileset(dir: staging.pipelineLibDir,
                        prefix: "${archivePrefix}/pipeline-lib") {
                }

                tarfileset(file: "${project.buildDir}/manual-upgrade.sh", prefix: archivePrefix, mode: 744)

                tarfileset(dir: distExtension.archiveDataDir,
                        prefix: archivePrefix) {
                    include(name: "README.txt")
                }
                tarfileset(dir: project.buildDir,
                        prefix: getArchivePrefix()) {
                    include(name: "VERSION")
                    include(name: "labkeywebapp/**")
                    include(name: "nlp/**")
                }
                tarfileset(dir: project.buildDir,
                        prefix: archivePrefix) {
                    include(name: "labkey.xml")
                }
            }
        }
        else
        {
            ant.tar(tarfile: getTarArchivePath(),
                    longfile: "gnu",
                    compression: "gzip") {
                tarfileset(dir: getModulesDir(),
                        prefix: "${archivePrefix}/modules") {
                    include(name: "*.module")
                }
            }
        }

    }

    private void zipArchives()
    {
        String archivePrefix = this.getArchivePrefix()
        if (makeDistribution)
        {
            ant.zip(destfile: getZipArchivePath()) {
                zipfileset(dir: "${project.rootProject.buildDir}/staging/labkeyWebapp",
                        prefix: "${archivePrefix}/labkeywebapp") {
                    exclude(name: "WEB-INF/classes/distribution")
                }
                zipfileset(dir: getModulesDir(),
                        prefix: "${archivePrefix}/modules") {
                    include(name: "*.module")
                }
                project.project(":server").configurations.tomcatJars.getFiles().collect({
                    tomcatJar ->
                        zipfileset(file: tomcatJar.path,
                                prefix: "${archivePrefix}/tomcat-lib")
                })
                zipfileset(dir: "${project.rootProject.buildDir}/staging/pipelineLib",
                        prefix: "${archivePrefix}/pipeline-lib")
                zipfileset(dir: "${project.rootProject.projectDir}/external/windows/core",
                        prefix: "${archivePrefix}/bin") {
                    include(name: "**/*")
                    exclude(name: "**/.svn")
                }

                if (includeMassSpecBinaries)
                {
                    zipfileset(dir: "${project.rootProject.projectDir}/external/windows/",
                            prefix: "${archivePrefix}/bin") {
                        exclude(name: "**/.svn")
                        include(name: "tpp/**/*")
                        include(name: "comet/**/*")
                        include(name: "msinspect/**/*")
                        include(name: "labkey/**/*")
                        include(name: "pwiz/**/*")
                    }
                }

                zipfileset(dir: distExtension.archiveDataDir,
                        prefix: "${archivePrefix}") {
                    include(name: "README.txt")
                }
                zipfileset(dir: "${project.buildDir}/",
                        prefix: "${archivePrefix}") {
                    include(name: "VERSION")
                    include(name: "labkeywebapp/**")
                    include(name: "nlp/**")
                }
                zipfileset(dir: "${project.buildDir}/",
                        prefix: "${archivePrefix}") {
                    include(name: "labkey.xml")
                }
            }
        }
        else
        {
            ant.zip(destfile: getZipArchivePath()) {
                zipfileset(dir: getModulesDir(),
                        prefix: "${archivePrefix}/modules") {
                    include(name: "*.module")
                }
            }
        }

    }

    private void createDistributionFiles()
    {
        writeDistributionFile()
        writeVersionFile()
        // copy the manual-update script to the build directory so we can fix the line endings.
        project.copy({CopySpec copy ->
            copy.from(distExtension.archiveDataDir)
            copy.include "manual-upgrade.sh"
            copy.into project.buildDir
        })
        project.ant.fixcrlf (srcdir: project.buildDir, includes: "manual-upgrade.sh VERSION", eol: "unix")
    }

    File getDistributionFile()
    {
        File distExtraDir = new File(project.buildDir, DistributionExtension.DIST_FILE_DIR)
        return new File(distExtraDir,  DistributionExtension.DIST_FILE_NAME)
    }

    private void writeDistributionFile()
    {
        Files.write(getDistributionFile().toPath(), project.name.getBytes())
    }

    File getVersionFile()
    {
        return new File(project.buildDir.absolutePath, DistributionExtension.VERSION_FILE_NAME)
    }

    private void writeVersionFile()
    {
        Files.write(getVersionFile().toPath(), ((String) project.version).getBytes())
    }
}
