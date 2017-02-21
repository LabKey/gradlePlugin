package org.labkey.gradle.task

import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.SystemUtils
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecSpec
import org.labkey.gradle.plugin.DistributionExtension
import org.labkey.gradle.plugin.StagingExtension
import org.labkey.gradle.util.PropertiesUtils

import java.nio.file.Files
import java.nio.file.Paths

class ModuleDistribution extends DistributionTask
{
    public static final String ALL_DISTRIBUTIONS = "all"

    public static final String[] STANDARD_MODULES = [
            ':server:internal',
            ':server:api',
            ':server:modules:announcements',
            ':server:modules:audit',
            ':server:modules:bigiron',
            ':server:modules:core',
            ':server:modules:dataintegration',
            ':server:modules:elisa',
            ':server:modules:elispotassay',
            ':server:modules:experiment',
            ':server:customModules:fcsexpress',
            ':server:modules:filecontent',
            ':server:modules:flow',
            ':server:modules:issues',
            ':server:modules:list',
            ':server:modules:luminex',
            ':server:modules:microarray',
            ':server:modules:ms1',
            ':server:modules:ms2',
            ':server:modules:nab',
            ':server:modules:pipeline',
            ':server:modules:query',
            ':server:modules:search',
            ':server:modules:study',
            ':server:modules:survey',
            ':server:customModules:targetedms',
            ':server:modules:visualization',
            ':server:modules:wiki'
    ]

    // TODO would like to declare this as the output directory but need to supply a value during configuration.
    // Need to figure out order of initialization.
//    @OutputDirectory
    File distributionDir

    String binPrefix
    DistributionExtension distExtension

    ModuleDistribution()
    {
        description = "Make a LabKey modules distribution"
        this.dependsOn(project.project(":server").tasks.stageTomcatJars)
    }

    @TaskAction
    void doAction()
    {
        init()

        createDistributionFiles()
        gatherModules()
        packageRedistributables()

    }

    private void init()
    {
        distExtension = project.getExtensions().findByType(DistributionExtension.class)

        if (versionPrefix == null)
            versionPrefix = "Labkey${project.installerVersion}${extraFileIdentifier}"
        binPrefix = "${versionPrefix}-bin"

        distributionDir = project.file("${dir}/${subDirName}")
        new File(distExtension.modulesDir).deleteDir()
        distributionDir.deleteDir()
        distributionDir.mkdirs()
    }

    private void gatherModules()
    {
        project.copy{CopySpec copy ->
            copy.from { project.configurations.distribution }
            copy.into distExtension.modulesDir
        }
    }

    private void packageRedistributables()
    {
        project.mkdir(project.file(distributionDir).getAbsolutePath())

        copyLibXml()
        packageInstallers()
        packageArchives()
    }

    private void copyLibXml()
    {
        Properties copyProps = new Properties()
        //The Windows installer only supports Postgres, which it also installs.
        copyProps.put("jdbcURL", "jdbc:postgresql://localhost/labkey")
        copyProps.put("jdbcDriverClassName", "org.postgresql.Driver")

        project.copy({ CopySpec copy ->
            copy.from("${project.rootProject.projectDir}/webapps")
            copy.include("labkey.xml")
            copy.into(installerBuildDir)
            copy.filter({ String line ->
                return PropertiesUtils.replaceProps(line, copyProps, true)
            })
        })
    }

    private void packageInstallers()
    {
        if (includeWindowsInstaller && SystemUtils.IS_OS_WINDOWS) {
            project.exec({ ExecSpec spec ->
                spec.commandLine FilenameUtils.separatorsToSystem("${distExtension.installerSrcDir}/nsis2.46/makensis.exe")
                spec.args = [
                        "/DPRODUCT_VERSION=\"${project.version}\"",
                        "/DPRODUCT_REVISION=\"${project.vcsRevision}\"",
                        FilenameUtils.separatorsToSystem("${distExtension.installerSrcDir}/labkey_installer.nsi")
                ]
            })

            project.copy({ CopySpec copy ->
                copy.from("${installerBuildDir}/..") // makensis puts the installer into build/installer without the project name subdirectory
                copy.include("Setup_includeJRE.exe")
                copy.into(distributionDir)
                copy.rename("Setup_includeJRE.exe", "${versionPrefix}-Setup.exe")
            })
        }
    }

    private void packageArchives()
    {
        if (includeTarGZDistribution)
        {
            tarArchives()
        }
        if (includeZipDistribution)
        {
            zipArchives()
        }
    }

    private void tarArchives()
    {

        StagingExtension staging = project.getExtensions().getByType(StagingExtension.class)

        ant.tar(tarfile:"${distributionDir}/${binPrefix}.tar.gz",
                longfile: "gnu",
                compression: "gzip" ) {
            tarfileset(dir: staging.webappDir,
                   prefix:"${binPrefix}/labkeywebapp") {
                exclude(name: "WEB-INF/classes/distribution")
            }
            tarfileset(dir: distExtension.modulesDir,
                    prefix: "${binPrefix}/modules") {
                include(name: "*.module")
            }
            tarfileset(dir: distExtension.extraSrcDir,
                    prefix: "${binPrefix}/") {
                include(name:"**/*")
            }
            tarfileset(dir: staging.tomcatLibDir, prefix: "${binPrefix}/tomcat-lib") {
                // this exclusion is necessary because for some reason when buildFromSource=false,
                // the tomcat bootstrap jar is included in the staged libraries and the LabKey boostrap jar is not.
                // Not sure why.
                exclude(name: "bootstrap.jar")
            }

            if (includeMassSpecBinaries) {
                tarfileset(dir: "${project.rootProject.projectDir}/external/windows/msinspect",
                        prefix: "${binPrefix}/bin") {
                    include(name: "**/*.jar")
                    exclude(name: "**/.svn")
                }
            }
            // TODO this should not be necessary once we figure out why buildFromSource=false doesn't pick this up
            tarfileset(file: project.project(":server:bootstrap").tasks.jar.outputs.getFiles().asPath,
                    prefix: "${binPrefix}/tomcat-lib/")

            tarfileset(dir: staging.pipelineLibDir,
                    prefix: "${binPrefix}/pipeline-lib") {
            }

            tarfileset(file: "${installerBuildDir}/manual-upgrade.sh", prefix: binPrefix, mode: 744)

            tarfileset(dir: distExtension.archiveDataDir,
                    prefix: binPrefix) {
                include(name:"README.txt")
            }
            tarfileset(dir: installerBuildDir,
                    prefix: binPrefix) {
                include(name:"VERSION")
            }
            tarfileset(dir: installerBuildDir,
                    prefix: binPrefix) {
                include(name:"labkey.xml")
            }
        }
    }

    private void zipArchives()
    {
        ant.zip(destfile: "${distributionDir}/${binPrefix}.zip") {
            zipfileset(dir:"${project.rootProject.buildDir}/staging/labkeyWebapp",
                    prefix: "${binPrefix}/labkeywebapp") {
                exclude(name:"WEB-INF/classes/distribution")
            }
            zipfileset(dir: distExtension.modulesDir,
                    prefix: "${binPrefix}/modules") {
                include(name:"*.module")
            }
            zipfileset(dir: distExtension.extraSrcDir,
                    prefix: "${binPrefix}/") {
                include(name:"**/*")
            }
            project.project(":server").configurations.tomcatJars.getFiles().collect({
                tomcatJar ->
                    zipfileset(file: tomcatJar.path,
                            prefix: "${binPrefix}/tomcat-lib")
            })
            zipfileset(dir:"${project.rootProject.buildDir}/staging/pipelineLib",
                    prefix: "${binPrefix}/pipeline-lib")
            zipfileset(dir:"${project.rootProject.projectDir}/external/windows/core",
                    prefix: "${binPrefix}/bin") {
                include(name:"**/*")
                exclude(name:"**/.svn")
            }

            if (includeMassSpecBinaries) {
                zipfileset(dir:"${project.rootProject.projectDir}/external/windows/",
                        prefix: "${binPrefix}/bin") {
                    exclude(name:"**/.svn")
                    include(name:"tpp/**/*")
                    include(name:"comet/**/*")
                    include(name:"msinspect/**/*")
                    include(name:"labkey/**/*")
                    include(name:"pwiz/**/*")
                }
            }

            zipfileset(dir: distExtension.archiveDataDir,
                    prefix: "${binPrefix}") {
                include(name: "README.txt")
            }
            zipfileset(dir:"${installerBuildDir}/",
                    prefix: "${binPrefix}") {
                include(name:"VERSION")
            }
            zipfileset(dir:"${installerBuildDir}/",
                    prefix: "${binPrefix}") {
                include(name:"labkey.xml")
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
            copy.into installerBuildDir
        })
        project.ant.fixcrlf (srcdir: installerBuildDir, includes: "manual-upgrade.sh VERSION", eol: "unix")
    }


    private void writeDistributionFile()
    {
        File distExtraDir = new File(project.rootProject.buildDir, DistributionExtension.DIST_FILE_DIR)
        if (!distExtraDir.exists())
            distExtraDir.mkdirs()
        Files.write(Paths.get(distExtraDir.absolutePath, DistributionExtension.DIST_FILE_NAME), project.name.getBytes())
    }

    private void writeVersionFile()
    {
        Files.write(Paths.get(installerBuildDir.absolutePath, DistributionExtension.VERSION_FILE_NAME), ((String) project.version).getBytes())
    }
}
