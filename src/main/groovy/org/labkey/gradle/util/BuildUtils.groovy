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
package org.labkey.gradle.util

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.labkey.gradle.plugin.ServerBootstrap
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.ModuleExtension
import org.labkey.gradle.plugin.extension.TeamCityExtension

import java.nio.file.Files
import java.nio.file.Paths
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Static utility methods and constants for use in the build and settings scripts.
 */
class BuildUtils
{
    public static final String BUILD_FROM_SOURCE_PROP = "buildFromSource"
    public static final String BUILD_CLIENT_LIBS_FROM_SOURCE_PROP = "buildClientLibsFromSource"
    public static final String SERVER_MODULES_DIR = "server/modules"
    public static final String PLATFORM_MODULES_DIR = "server/modules/platform"
    public static final String COMMON_ASSAYS_MODULES_DIR = "server/modules/commonAssays"
    public static final String CUSTOM_MODULES_DIR = "server/customModules"
    public static final String CUSTOM_MODULES_GIT_DIR = "server/modules/customModules"
    public static final String OPTIONAL_MODULES_DIR = "server/optionalModules"
    public static final String EXTERNAL_MODULES_DIR = "externalModules"


    public static final List<String> EHR_MODULE_NAMES = [
            "EHR_ComplianceDB",
            "WNPRC_EHR",
            "cnprc_ehr",
            "snprc_ehr",
            "ehr",
            "onprc_ehr",
            "tnprc_ehr"
    ]

    // a set of directory paths in which to look for module directories
    public static final List<String> SERVER_MODULE_DIRS = [
            SERVER_MODULES_DIR,
            PLATFORM_MODULES_DIR,
            COMMON_ASSAYS_MODULES_DIR,
            CUSTOM_MODULES_DIR,
            OPTIONAL_MODULES_DIR,
            CUSTOM_MODULES_GIT_DIR
    ]

    public static final List<String> EHR_EXTERNAL_MODULE_DIRS = [
            "externalModules/labModules",
            "externalModules/onprcEHRModules",
            "externalModules/cnprcEHRModules",
            "externalModules/snprcEHRModules",
            "externalModules/DISCVR"
    ]

    public static final List<String> EXTERNAL_MODULE_DIRS = ["externalModules/scharp"] + EHR_EXTERNAL_MODULE_DIRS

    // matches on: name-X.Y.Z-SNAPSHOT.jar, name-X.Y.Z.word[-SNAPSHOT][-classifier].jar, name-X.Y.Z-SNAPSHOT-classifier.jar, name-X.Y.Z_branch-SNAPSHOT.jar, name-X.Y.Z_branch-SNAPSHOT-classifier.extension name-X.Y.Z.extension
    // Groups are:
    //    1: name
    //    2: version (including dash, numbers, dots, snapshot and branch and classification)
    //    3: version (excluding dash and classifier (X.Y.Z_branch-SNAPSHOT))
    //    4: all but major version number (.Y.Z)
    //    5: branch (including _)
    //    6: snapshot (including -)
    //    7: classifier (including -)
    //    8: extension
    public static final Pattern VERSIONED_ARTIFACT_NAME_PATTERN = Pattern.compile("^(.*?)(-(\\d+(\\.\\w+)*(_.+)?(-SNAPSHOT)?)(-\\w+)?)?\\.([^.]+)")
    public static final int ARTIFACT_NAME_INDEX = 1
    public static final int ARTIFACT_VERSION_INDEX = 2
    public static final int ARTIFACT_CLASSIFIER_INDEX = 7
    public static final int ARTIFACT_EXTENSION_INDEX = 8

    // the set of modules required for minimal LabKey server functionality
    static List<String> getBaseModules(Gradle gradle)
    {
        return [
                getApiProjectPath(gradle),
                getBootstrapProjectPath(gradle),
                getRemoteApiProjectPath(gradle),
                getInternalProjectPath(gradle),
                getPlatformModuleProjectPath(gradle, "audit"),
                getPlatformModuleProjectPath(gradle, "core"),
                getPlatformModuleProjectPath(gradle, "experiment"),
                getPlatformModuleProjectPath(gradle, "filecontent"),
                getPlatformModuleProjectPath(gradle, "pipeline"),
                getPlatformModuleProjectPath(gradle, "query")
        ]
    }

    /**
     * This includes modules that are required for any LabKey server build (e.g., bootstrap, api, internal)
     * @param settings the settings
     */
    static void includeBaseModules(Settings settings)
    {
        includeModules(settings, getBaseModules(settings.gradle))
    }

    /**
     * This includes the :server:test project as well as the modules in the server/test/modules directory
     * @param settings
     * @param rootDir root directory of the project
     */
    static void includeTestModules(Settings settings, File rootDir)
    {
        settings.include "${getTestProjectPath(settings.gradle)}:data:qc"
        settings.include getTestProjectPath(settings.gradle)
        includeModules(settings, rootDir, ["${convertPathToRelativeDir(getTestProjectPath(settings.gradle))}/modules"], [])
    }

    static void includeModules(Settings settings, List<String> modules)
    {
        settings.include modules.toArray(new String[0])
    }

    /**
     * Can be used in a gradle settings file to include the projects in a particular directory.
     * @param rootDir - the root directory for the gradle build (project.rootDir)
     * @param moduleDirs - the list of directories that are parents of module directories to be included
     * @param excludedModules - a list of directory names that are to be excluded from the build configuration (e.g., movies)
     */
    static void includeModules(Settings settings, File rootDir, List<String> moduleDirs, List<String> excludedModules)
    {
        includeModules(settings, rootDir, moduleDirs, excludedModules, false)
    }

    static void includeModules(Settings settings, File rootDir, List<String> moduleDirs, List<String> excludedModules, Boolean includeModuleContainers)
    {
        // find the directories in each of the moduleDirs that meet our selection criteria
        moduleDirs.each { String path ->
            if (path.contains("*"))
            {
                ModuleFinder finder = new ModuleFinder(rootDir, path, excludedModules)
                Files.walkFileTree(Paths.get(rootDir.getAbsolutePath()), finder)
                finder.modulePaths.each{String modulePath ->
                    settings.include modulePath
                }
            }
            else
            {
                File directory = new File(rootDir, path)
                if (directory.exists())
                {
                    String prefix = convertDirToPath(rootDir, directory)
                    Collection<File> potentialModules = directory.listFiles().findAll { File f ->
                        // exclude non-directories, explicitly excluded names, and directories beginning with a .
                        f.isDirectory() && !excludedModules.contains(f.getName()) &&  !(f.getName() =~ "^\\..*") && !f.getName().equals("node_modules")
                    }
                    settings.include potentialModules.collect {
                        (String) "${prefix}:${it.getName()}"
                    }.toArray(new String[0])

                    if (includeModuleContainers)
                    {
                        List<String> potentialModuleContainers = potentialModules.findAll { File f ->
                            !new File(f, ModuleExtension.MODULE_PROPERTIES_FILE).exists() && !f.getName().equals("test")
                        }.collect {
                            (String) "${path}/${it.getName()}"
                        }
                        includeModules(settings, rootDir, potentialModuleContainers, excludedModules, false)
                    }
                }
            }
        }
    }

    static String convertPathToRelativeDir(String path) {
        String relativePath = path.startsWith(":") ? path.substring(1) : path;
        relativePath.replace(":", "/");
    }

    static String convertDirToPath(File rootDir, File directory)
    {
        String relativePath = directory.absolutePath - rootDir.absolutePath
        return  relativePath.replaceAll("[\\\\\\/]", ":")
    }

    static boolean shouldBuildFromSource(Project project)
    {
        return whyNotBuildFromSource(project, BUILD_FROM_SOURCE_PROP).isEmpty()
    }

    static List<String> whyNotBuildFromSource(Project project, String property)
    {
        List<String> reasons = []
        // TODO the downloadLabKeyModules might be better as some sort of parameter to the addLabKeyDependencies
        // method, but I think this will allow testing out the feature of having module dependencies declared in build.gradle files
        if (!project.projectDir.exists() && project.hasProperty("downloadLabKeyModules"))
            reasons.add("Project directory ${project.projectDir} does not exist.")
        String propValue = project.hasProperty(property) ? project.property(property) : null
        String value = TeamCityExtension.getTeamCityProperty(project, property, propValue)
        if (value == null)
        {
            reasons.add("Project does not have ${property} property")
            if (isSvnModule(project))
                reasons.add("svn module without ${property} property set to true")
        }
        else if (!Boolean.valueOf((String) value))
            reasons.add("${property} property is false")

        return reasons
    }

    static boolean shouldBuildClientLibsFromSource(Project project)
    {
        return whyNotBuildFromSource(project, BUILD_CLIENT_LIBS_FROM_SOURCE_PROP).isEmpty()
    }

    static String getPlatformModuleProjectPath(Gradle gradle, String name)
    {
        // without the toString call below, you get the following error:
        // Caused by: java.lang.ArrayStoreException: arraycopy: element type mismatch: can not cast one of the elements of
        // java.lang.Object[] to the type of the destination array, java.lang.String
        return "${getPlatformProjectPath(gradle)}:${name}".toString();
    }

    // The gradle path to the project containing the platform (base) modules (e.g., core)
    static String getPlatformProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "platformProjectPath", ":server:modules")
    }


    static String getCommonAssayModuleProjectPath(Gradle gradle, String name)
    {
        return "${getCommonAssaysProjectPath(gradle)}:${name}".toString();
    }

    // The gradle path to the project containing the common assays
    static String getCommonAssaysProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "commonAssaysProjectPath", ":server:modules")
    }

    static boolean isApi(Project project)
    {
        return project.path.equals(getApiProjectPath(project.gradle))
    }

    static String getApiProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "apiProjectPath", ":server:api")
    }

    static String getBootstrapProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "bootstrapProjectPath", ":server:bootstrap")
    }

    static String getInternalProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "internalProjectPath", ":server:internal")
    }

    static String getNodeBinProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "nodeBinProjectPath", getPlatformModuleProjectPath(gradle, "core"))
    }

    static String getRemoteApiProjectPath(Gradle gradle)
    {

        return getProjectPath(gradle, "remoteApiProjectPath", ":remoteapi:java")
    }

    static String getSchemasProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "schemasProjectPath", ":schemas")
    }

    static String getTestProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "testProjectPath", ":server:test")
    }

    static boolean isGitModule(Project project)
    {
        return project.file(".git").exists()
    }

    static boolean isSvnModule(Project project)
    {
        return !isGitModule(project)
    }

    static String getVersionNumber(Project project)
    {
        if (project.hasProperty("versioning"))
        {
            String branch = project.versioning.info.branchId
            // When a git module is on the "master" branch in git, this corresponds to the alpha branch
            // at the root and we want to use that version for consistency with the SVN modules
            if (branch.equalsIgnoreCase("master"))
                return project.rootProject.version
            else if (["trunk", "develop", "none"].contains(branch) || branch.toLowerCase().matches("release.*-snapshot"))
                return project.labkeyVersion
            else
            {
                String currentVersion = project.labkeyVersion
                return currentVersion.replace("-SNAPSHOT", "_${branch}-SNAPSHOT")
            }
        }
        return project.labkeyVersion
    }

    /**
     * Gets the versioning string to be inserted into distribution artifacts.  This string has a slightly different
     * format depending on the branch in which the distributions are created:
     *     Trunk - 19.1-SNAPSHOT-62300.800 (<Current labkeyVersion>-<VCSRevision>.<BuildCounter>)
     *     Sprint (deprecated) - 17.3Sprint3-46992.4 (<labkey release version>Sprint<sprint number>-<VCSRevision>.<BuildNumber>)
     *     Alpha - 19.1Alpha3-62239.3 (<labkey release version>Alpha<sprint number>-<VCSRevision>.<BuildCounter>)
     *     Beta - 17.3Beta-47540.6 (<labkey release version>Beta-<VCSRevision>.<BuildNumber>)
     *     (Beta means we are in a release branch, but have not yet released and updated from the snapshot version)
     *     Release - 17.3-47926.26 (<labkey release version>-<VCSRevision>.<BuildNumber>)
     * See Issue 31165.
     * @param project the distribution project
     * @return the version string for this distribution project
     */
    static String getDistributionVersion(Project project)
    {
        String version = project.labkeyVersion
        project.logger.info("${project.path} version ${version}")
        if (project.hasProperty("versioning"))
        {
            String rootBranch = project.rootProject.versioning.info.branchId
            String lowerBranch = rootBranch.toLowerCase()
            project.logger.info("${project.path} rootBranch ${rootBranch}")

            if (!["trunk", "master", "develop", "none"].contains(rootBranch))
            {
                if (lowerBranch.startsWith("alpha")) /* e.g. alpha_19.1_3 */
                {
                    version = version.replace("-SNAPSHOT", "")
                    version += "Alpha"
                    String[] nameParts = rootBranch.split("_")
                    if (nameParts.length != 3)
                        project.logger.error("Root branch name '${rootBranch}' not as expected.  Distribution name may not be as expected.");
                    else
                        version += nameParts[2]
                }
                else if (lowerBranch.startsWith("release") &&
                        project.labkeyVersion.contains("-SNAPSHOT")) /* e.g. release18.3-SNAPSHOT */
                {
                    version = version.replace("-SNAPSHOT", "Beta");
                }
            }
            // on trunk at the root and a feature branch in the project (rare, but possible, I guess)
            String branch = project.versioning.info.branchId
            project.logger.info("${project.path} branch ${branch}, version so far ${version}, vcsRevision ${project.rootProject.vcsRevision}")
            if (!["trunk", "master", "develop", "none"].contains(branch))
                version = version.replace("-SNAPSHOT", "_${branch}-SNAPSHOT")
            version += "-" + project.rootProject.vcsRevision
            TeamCityExtension extension  = project.getExtensions().findByType(TeamCityExtension.class)
            if (extension != null)
            {
                String buildNumber = extension.getTeamCityProperty("build.number")
                if (!StringUtils.isEmpty(buildNumber))
                {
                    // Sometimes (probably when the root VCS is SVN), the build.number has the
                    // format <vcs revision>.<build counter> and sometimes (probably when the
                    // VCS is git) it's just <build counter>.  Preparing for the future.
                    String[] numberParts = buildNumber.split("\\.")
                    version += ".${numberParts[numberParts.length-1]}"
                }
            }
            project.logger.info("${project.path} version ${version}")
        }
        return version
    }

    static String getModuleFileVersion(Project project)
    {
        String version = project.version
        if (project.hasProperty("versioning"))
        {
            String branch = project.versioning.info.branchId
            // When a git module is on the "master" branch in git, this corresponds to the alpha branch
            // at the root and we want to use that version for consistency with the SVN modules
            if (branch.equalsIgnoreCase("master"))
            {
                version = project.rootProject.version
            }
        }
        return version
    }

    /**
     * Returns a module version to be used as a LabKey Module property.  This must be a decimal
     * number (e.g., 19.1), so we use only the first two parts of the artifact version number
     * @param project The project whose artifact version is to be translated to a LabKey module version
     * @return the LabKey module version string
     */
    static String getLabKeyModuleVersion(Project project)
    {
        String version = project.version
        // matches to a.b.c.d_rfb_123-SNAPSHOT or a.b.c.d-SNAPSHOT
        Matcher matcher = Pattern.compile("([^_-]+)[_-].*").matcher(version)
        if (matcher.matches())
            version = matcher.group(1)
        String[] versionParts = version.split("\\.")
        if (versionParts.size() > 2)
            version = versionParts[0] + "." + versionParts[1]
        return version
    }

    static Properties getStandardVCSProperties(project)
    {
        Properties ret = new Properties()
        if (project.plugins.hasPlugin("org.labkey.versioning"))
        {
            Project vcsProject = project
            while (vcsProject.versioning.info.url == "No VCS" && vcsProject != project.rootProject)
            {
                vcsProject = vcsProject.parent
            }
            vcsProject.println("${project.path} versioning info ${ vcsProject.versioning.info}")
            ret.setProperty("VcsURL", vcsProject.versioning.info.url)
            if (vcsProject.versioning.info.branch != null)
                ret.setProperty("VcsBranch", vcsProject.versioning.info.branch)
            if (vcsProject.versioning.info.tag != null)
               ret.setProperty("VcsTag", vcsProject.versioning.info.tag)
            ret.setProperty("VcsRevision", vcsProject.versioning.info.commit)
            ret.setProperty("BuildNumber", (String) TeamCityExtension.getTeamCityProperty(project, "build.number", vcsProject.versioning.info.build))
        }
        else
        {
            ret.setProperty("VcsBranch", "Unknown")
            ret.setProperty("VcsTag", "Unknown")
            ret.setProperty("VcsURL", "Unknown")
            ret.setProperty("VcsRevision", "Unknown")
            ret.setProperty("BuildNumber", "Unknown")
        }
        return ret
    }

    // Default Tomcat libraries for building Java modules and server API
    private static List TOMCAT_LIBS = [
            'tomcat-api',
            'tomcat-catalina',
            'tomcat-jasper',
            'tomcat-jsp-api',
            'tomcat-util',
            'tomcat-websocket-api',
            'tomcat7-websocket'
    ]

    static void setTomcatLibs(List<String> libs)
    {
        TOMCAT_LIBS = new ArrayList(libs)
    }

    static void addTomcatBuildDependencies(Project project, String configuration)
    {
        if (!"${project.apacheTomcatVersion}".startsWith("7."))
            TOMCAT_LIBS.replaceAll({it.replace('tomcat7-', 'tomcat-')})
        for (String lib : TOMCAT_LIBS)
            project.dependencies.add(configuration, "org.apache.tomcat:${lib}:${project.apacheTomcatVersion}")
    }

    static void addLabKeyDependency(Map<String, Object> config)
    {
        if (config.get('transitive') != null)
        {
            addLabKeyDependency(
                    (Project) config.get("project"),
                    (String) config.get("config"),
                    (String) config.get("depProjectPath"),
                    (String) config.get("depProjectConfig"),
                    (String) config.get("depVersion"),
                    (String) config.get("depExtension"),
                    (Boolean) config.get('transitive'),
                    (Closure) config.get("specialParams")
            )
        }
        else
        {
            addLabKeyDependency(
                    (Project) config.get("project"),
                    (String) config.get("config"),
                    (String) config.get("depProjectPath"),
                    (String) config.get("depProjectConfig"),
                    (String) config.get("depVersion"),
                    (String) config.get("depExtension"),
                    (Closure) config.get("specialParams")
            )
        }
    }

    static void addModuleDistributionDependency(Project distributionProject, String depProjectPath, String config)
    {
        if (distributionProject.configurations.findByName(config) == null)
            distributionProject.configurations {
                config
            }
        distributionProject.logger.info("${distributionProject.path}: adding ${depProjectPath} as dependency for config ${config}")
        addLabKeyDependency(project: distributionProject, config: config, depProjectPath: depProjectPath, depProjectConfig: "published", depExtension: "module", depVersion: distributionProject.labkeyVersion)
    }

    static void addModuleDistributionDependency(Project distributionProject, String depProjectPath)
    {
        addLabKeyDependency(project: distributionProject, config: "distribution", depProjectPath: depProjectPath, depProjectConfig: "published", depExtension: "module", depVersion: distributionProject.labkeyVersion)
    }

    static void addModuleDistributionDependencies(Project distributionProject, List<String> depProjectPaths)
    {
        addModuleDistributionDependencies(distributionProject, depProjectPaths, "distribution")
    }

    static void addModuleDistributionDependencies(Project distributionProject, List<String> depProjectPaths, String config)
    {
        depProjectPaths.each{
            String path -> addModuleDistributionDependency(distributionProject, path, config)
        }
    }

    static void addLabKeyDependency(Project parentProject,
                                           String parentProjectConfig,
                                           String depProjectPath,
                                           String depProjectConfig,
                                           String depVersion,
                                           String depExtension) {
        addLabKeyDependency(parentProject, parentProjectConfig, depProjectPath, depProjectConfig, depVersion, depExtension, null)
    }

    static void addLabKeyDependency(Project parentProject,
                                           String parentProjectConfig,
                                           String depProjectPath,
                                           String depProjectConfig,
                                           String depVersion,
                                           String depExtension,
                                           Closure specialParams)
    {
       addLabKeyDependency(parentProject, parentProjectConfig, depProjectPath, depProjectConfig, depVersion, depExtension,
              !"jars".equals(parentProjectConfig),  specialParams)
    }

    static void addLabKeyDependency(Project parentProject,
                                    String parentProjectConfig,
                                    String depProjectPath,
                                    String depProjectConfig,
                                    String depVersion,
                                    String depExtension,
                                    Boolean isTransitive,
                                    Closure closure
                                    )
    {
        Project depProject = parentProject.rootProject.findProject(depProjectPath)

        if (depProject != null && shouldBuildFromSource(depProject))
        {
            parentProject.logger.debug("Found project ${depProjectPath}; building ${depProjectPath} from source")
            if (depProjectConfig != null)
                parentProject.dependencies.add(parentProjectConfig, parentProject.dependencies.project(path: depProjectPath, configuration: depProjectConfig, transitive: isTransitive), closure)
            else
                parentProject.dependencies.add(parentProjectConfig, parentProject.dependencies.project(path: depProjectPath, transitive: isTransitive), closure)
        }
        else
        {
            if (depProject == null)
            {
                parentProject.logger.debug("${depProjectPath} project not found; assumed to be external.")
                if (depVersion == null)
                    depVersion = parentProject.version
            }
            else
            {
                parentProject.logger.debug("${depProjectPath} project found but not building from source because: "
                        + whyNotBuildFromSource(parentProject, BUILD_FROM_SOURCE_PROP).join("; "))
                if (depVersion == null)
                    depVersion = depProject.version
            }

            // TODO I don't think this combinedClosure works. Change to just pass transitive through in the add
            // and then pass on the closure without evaluating it.
            def combinedClosure =  {
                transitive isTransitive
                if (closure != null)
                    closure()
            }

            parentProject.dependencies.add(parentProjectConfig, getLabKeyArtifactName(parentProject, depProjectPath, depVersion, depExtension), combinedClosure)
        }
    }

    static String getLabKeyArtifactName(Project project, String projectPath, String version, String extension)
    {
        String moduleName
        String group = extension.equals("module") ? LabKeyExtension.MODULE_GROUP : LabKeyExtension.API_GROUP
        if (projectPath.endsWith(getRemoteApiProjectPath(project.gradle).substring(1)))
        {
            group = LabKeyExtension.LABKEY_GROUP
            moduleName = "labkey-client-api"
        }
        else if (projectPath.equals(getBootstrapProjectPath(project.gradle)))
        {
            group = LabKeyExtension.LABKEY_GROUP
            moduleName = ServerBootstrap.JAR_BASE_NAME
        }
        else
        {
            int index = projectPath.lastIndexOf(":")
            moduleName = projectPath
            if (index >= 0)
                moduleName = projectPath.substring(index + 1)
        }

        String versionString = version == null ? "" : ":$version"

        String extensionString = extension == null ? "" : "@$extension"

        return "${group}:${moduleName}${versionString}${extensionString}"
    }

    static String getRepositoryKey(Project project)
    {
        String repoKey = shouldPublishDistribution(project) ? "dist" : "libs"
        repoKey += project.version.endsWith("-SNAPSHOT") ? "-snapshot" : "-release"
        repoKey += "-local"

        return repoKey
    }

    static Boolean shouldPublish(project)
    {
        return project.hasProperty("doPublishing")
    }

    static Boolean shouldPublishDistribution(project)
    {
        return project.hasProperty("doDistributionPublish")
    }

    static Boolean isIntellij()
    {
        return System.properties.'idea.active'
    }

    static Boolean isIntellijGradleRefresh(project)
    {
        return project.getStartParameter().getSystemPropertiesArgs().get("idea.version") != null
    }

    static int compareVersions(thisVersion, thatVersion)
    {
        if (thisVersion == null &&  thatVersion != null)
            return -1
        if (thisVersion != null && thatVersion == null)
            return 1
        if (thisVersion == null && thatVersion == null)
            return 0

        String[] thisVersionParts = ((String) thisVersion).split("\\.")
        String[] thatVersionParts = ((String) thatVersion).split("\\.")
        int i = 0;
        while (i < thisVersionParts.length && i < thatVersionParts.length)
        {
            int thisPartNum = Integer.valueOf(thisVersionParts[i])
            int thatPartNum = Integer.valueOf(thatVersionParts[i])
            if (thisPartNum > thatPartNum)
                return 1
            if (thisPartNum < thatPartNum)
                return -1
            i++
        }
        if (i < thisVersionParts.length)
            return 1
        if (i < thatVersionParts.length)
            return -1
        return 0
    }

    static String getProjectPath(Gradle gradle, String propertyName, String defaultValue)
    {
        return gradle.hasProperty(propertyName) ? gradle.getProperty(propertyName) : defaultValue
    }
}
