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

import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Remote
import org.apache.commons.lang3.StringUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySubstitutions
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.plugin.extension.ModuleExtension
import org.labkey.gradle.plugin.extension.ServerDeployExtension
import org.labkey.gradle.plugin.extension.TeamCityExtension
import org.labkey.gradle.task.ModuleDistribution

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Static utility methods and constants for use in the build and settings scripts.
 */
class BuildUtils
{
    public static final String BUILD_FROM_SOURCE_PROP = "buildFromSource"
    public static final String USE_EMBEDDED_TOMCAT = "useEmbeddedTomcat"
    public static final String BUILD_CLIENT_LIBS_FROM_SOURCE_PROP = "buildClientLibsFromSource"
    public static final String SERVER_MODULES_DIR = "server/modules"
    public static final String PLATFORM_MODULES_DIR = "server/modules/platform"
    public static final String COMMON_ASSAYS_MODULES_DIR = "server/modules/commonAssays"
    public static final String CUSTOM_MODULES_DIR = "server/modules/customModules"
    private static final String RESTART_FILE_NAME = ".restartTrigger"

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
            CUSTOM_MODULES_DIR
    ]

    public static final List<String> EHR_EXTERNAL_MODULE_DIRS = [
            "externalModules/labModules",
            "externalModules/onprcEHRModules",
            "externalModules/cnprcEHRModules",
            "externalModules/snprcEHRModules",
            "externalModules/DISCVR"
    ]

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
    public static final String BOOTSTRAP_JAR_BASE_NAME = "labkeyBootstrap"

    // the set of modules required for minimal LabKey server functionality
    static List<String> getBaseModules(Gradle gradle)
    {
        return [
                getApiProjectPath(gradle),
                getBootstrapProjectPath(gradle),
                getPlatformModuleProjectPath(gradle, "audit"),
                getPlatformModuleProjectPath(gradle, "core"),
                getPlatformModuleProjectPath(gradle, "experiment"),
                getPlatformModuleProjectPath(gradle, "filecontent"),
                getPlatformModuleProjectPath(gradle, "pipeline"),
                getPlatformModuleProjectPath(gradle, "query"),
        ]
    }

    static boolean isBaseModule(Project project)
    {
        return getBaseModules(project.gradle).contains(project.path)
    }

    /**
     * This includes modules that are required for any LabKey server build (e.g., bootstrap, api, internal)
     * @param settings the settings
     */
    static void includeBaseModules(Settings settings)
    {
        includeBaseModules(settings, [])
    }

    /**
     * This includes modules that are required for any LabKey server build (e.g., bootstrap, api, internal)
     * @param settings the settings
     * @param excludedModules the list of module names or paths to exclude
     */
    static void includeBaseModules(Settings settings, List<String> excludedModules)
    {
        includeModules(settings, getBaseModules(settings.gradle), excludedModules)
    }

    /**
     * Add module dependencies for all the base modules to facilitate deploying a LabKey Server instance without building the base modules
     * @param project The project for the LabKey module being deployed.
     */
    static void addBaseModuleDependencies(Project project)
    {
        for (String path : getBaseModules(project.gradle))
        {
            if (path != getBootstrapProjectPath(project.gradle) && path != getRemoteApiProjectPath(project.gradle))
            {
                addLabKeyDependency(project: project, config: "modules", depProjectPath: path, depProjectConfig: 'published', depExtension: 'module')
            }
        }
    }

    /**
     * This includes the :server:test project as well as the modules in the server/test/modules directory
     * @param settings
     * @param rootDir root directory of the project
     */
    static void includeTestModules(Settings settings, File rootDir)
    {
        includeTestModules(settings, rootDir, [])
    }

    static void includeTestModules(Settings settings, File rootDir, List<String> excludedModulePaths)
    {
        String qcPath = "${getTestProjectPath(settings.gradle)}:data:qc"
        if (!excludedModulePaths.contains(qcPath))
            settings.include qcPath
        String testPath = getTestProjectPath(settings.gradle)
        if (!excludedModulePaths.contains(testPath)) {
            settings.include testPath
            includeModules(settings, rootDir, ["${convertPathToRelativeDir(testPath)}/modules"], excludedModulePaths)
        }
    }

    static void includeModules(Settings settings, List<String> modules, List<String> excludedModules)
    {
        modules.forEach(modulePath -> {
            if (!excludedModules.contains(modulePath))
                settings.include modulePath
        })
    }

    /**
     * Can be used in a gradle settings file to include the projects in a particular directory.
     * @param rootDir - the root directory for the gradle build (project.rootDir)
     * @param moduleDirs - the list of directories that are parents of module directories to be included
     * @param excludedModules - a list of directory names or fully qualified project paths that are to be excluded from the build configuration (e.g., movies)
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
                    if (!excludedModules.contains(modulePath))
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
                    List<String> includePaths =  potentialModules.collect {
                        (String) "${prefix}:${it.getName()}"
                    }
                    includePaths.forEach(includePath -> {
                        if (!excludedModules.contains(includePath))
                            settings.include includePath
                    })

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
        String relativePath = path.startsWith(":") ? path.substring(1) : path
        relativePath.replace(":", "/")
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
        else if (BuildFromSource.fromProperty(value) == BuildFromSource._FALSE)
            reasons.add("${property} property is not 'true'")

        return reasons
    }

    static boolean shouldBuildClientLibsFromSource(Project project)
    {
        return whyNotBuildFromSource(project, BUILD_CLIENT_LIBS_FROM_SOURCE_PROP).isEmpty()
    }

    static Project getConfigsProject(Project project)
    {
        return project.project(getConfigsProjectPath(project.gradle))
    }

    static String getConfigsProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "configsProjectPath", getServerProjectPath(gradle))
    }

    static Project getServerProject(Project project)
    {
        return project.findProject(getServerProjectPath(project.gradle))
    }

    static String getServerProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "serverProjectPath", ":server")
    }

    static String getPlatformModuleProjectPath(Gradle gradle, String name)
    {
        // without the toString call below, you get the following error:
        // Caused by: java.lang.ArrayStoreException: arraycopy: element type mismatch: can not cast one of the elements of
        // java.lang.Object[] to the type of the destination array, java.lang.String
        return "${getPlatformProjectPath(gradle)}:${name}".toString()
    }

    // The gradle path to the project containing the platform (base) modules (e.g., core)
    static String getPlatformProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "platformProjectPath", ":server:modules")
    }


    static String getCommonAssayModuleProjectPath(Gradle gradle, String name)
    {
        return "${getCommonAssaysProjectPath(gradle)}:${name}".toString()
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

    static String getEmbeddedProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "embeddedProjectPath", ":server:embedded")
    }

    static boolean haveMinificationProject(Gradle gradle)
    {
        return gradle.rootProject.findProject(getMinificationProjectPath(gradle)) != null
    }

    static String getMinificationProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "minificationProjectPath", ":server:minification")
    }

    static String getApiProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "apiProjectPath", ":server:modules:platform:api")
    }

    static String getBootstrapProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "bootstrapProjectPath", ":server:bootstrap")
    }

    static String getNodeBinProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "nodeBinProjectPath", getPlatformModuleProjectPath(gradle, "core"))
    }

    static String getRemoteApiProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "remoteApiProjectPath", ":remoteapi:labkey-api-java")
    }

    static String getJdbcApiProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "jdbcApiProjectPath", ":remoteapi:labkey-api-jdbc")
    }

    static String getLabKeyClientApiVersion(Project project)
    {
        return project.hasProperty('labkeyClientApiVersion') ? project.property('labkeyClientApiVersion') : project.labkeyVersion
    }

    static String getSchemasProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "schemasProjectPath", ":server:modules:platform:api")
    }

    static String getTestProjectPath(Gradle gradle)
    {
        return getProjectPath(gradle, "testProjectPath", ":server:testAutomation")
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
        String version = project.labkeyVersion

        if (project.hasProperty("versioning"))
        {
            String branch = project.versioning.info.branchId
            if (!["trunk", "develop", "master", "main", "none", ""].contains(branch) &&
                    !branch.toLowerCase().matches("release.*-snapshot"))
            {
                Matcher matcher = Pattern.compile(".*fb_(.+)").matcher(branch)
                if (matcher.matches()) {
                    branch = matcher.group(1) // Trim leading 'fb_' or 'XX.Y_fb_'
                }
                version = version.replace("-SNAPSHOT", "-${branch}-SNAPSHOT")
            }
        }

        return version
    }

    /**
     * Gets the versioning string to be inserted into distribution artifacts.  This string has a slightly different
     * format depending on the branch in which the distributions are created:
     *     Trunk/Develop - 20.11-SNAPSHOT-1500 (<Current labkeyVersion>[-<BuildCounter>])
     *     Beta - 20.11Beta-6 (<labkey release version>Beta[-<BuildCounter>])
     *       (Beta means we are in a release branch, but have not yet released and updated from the snapshot version)
     *     Release - 20.11.0-1 (<Current labkeyVersion>[-<BuildCounter>])
     * See Issue 31165.
     * @param project the distribution project. e.g. project(':distributions:community')
     * @return the version string for this distribution project
     */
    static String getDistributionVersion(Project project)
    {
        String distVersion = project.labkeyVersion
        project.logger.info("${project.path} version ${distVersion}")

        TeamCityExtension extension = project.getExtensions().findByType(TeamCityExtension.class)
        if (extension != null)
        {
            if (!extension.buildBranchDefault)
            {
                distVersion = distVersion.replace("-SNAPSHOT", "-${extension.buildBranch}-SNAPSHOT")
            }
            String buildNumber = extension.getTeamCityProperty("build.number")
            if (!StringUtils.isEmpty(buildNumber))
            {
                // Sometimes (probably when the root VCS is SVN), the build.number has the
                // format <vcs revision>.<build counter> and sometimes (probably when the
                // VCS is git) it's just <build counter>.  Preparing for the future.
                String[] numberParts = buildNumber.split("\\.")
                distVersion += "-${numberParts[numberParts.length-1]}"
            }
        }

        if (project.hasProperty("versioning"))
        {
            String rootBranch = project.rootProject.versioning.info.branchId
            project.logger.info("${project.path} rootBranch ${rootBranch}")
            if (rootBranch.startsWith("release") && /* e.g. release20.11-SNAPSHOT */
                    project.labkeyVersion.contains("-SNAPSHOT")) /* e.g. 20.11-SNAPSHOT */
            {
                distVersion = distVersion.replace("-SNAPSHOT", "Beta")
            }
            project.logger.info("${project.path} version ${distVersion}")
        }
        return distVersion
    }

    static boolean isOpenSource(Project project)
    {
        return project.hasProperty("isOpenSource") && Boolean.valueOf((String) project.property("isOpenSource"))
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
        String buildNumber =
                (String) TeamCityExtension.getTeamCityProperty(project, "system.teamcity.agent.dotnet.build_id", // Unique build ID
                        TeamCityExtension.getTeamCityProperty(project,"build.number", null))
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
            ret.setProperty("BuildNumber", buildNumber != null ? buildNumber : vcsProject.versioning.info.build)
        }
        else if (project.plugins.hasPlugin("net.nemerosa.versioning"))
        {
            // In our fork of the plugin (above), we added the url property to the VersioningInfo object
            Project vcsProject = project
            String url = getGitUrl(vcsProject)
            while (url == null && vcsProject != project.rootProject)
            {
                vcsProject = vcsProject.parent
                url = getGitUr(vcsProject)
            }
            vcsProject.println("${project.path} versioning info ${ vcsProject.versioning.info}")
            ret.setProperty("VcsURL", url)
            if (vcsProject.versioning.info.branch != null)
                ret.setProperty("VcsBranch", vcsProject.versioning.info.branch)
            if (vcsProject.versioning.info.tag != null)
                ret.setProperty("VcsTag", vcsProject.versioning.info.tag)
            ret.setProperty("VcsRevision", vcsProject.versioning.info.commit)
            ret.setProperty("BuildNumber", buildNumber != null ? buildNumber : vcsProject.versioning.info.build)
        }
        else
        {
            ret.setProperty("VcsBranch", "Unknown")
            ret.setProperty("VcsTag", "Unknown")
            ret.setProperty("VcsURL", "Unknown")
            ret.setProperty("VcsRevision", "Unknown")
            ret.setProperty("BuildNumber", buildNumber != null ? buildNumber : "Unknown")
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

    private static List LIMS_MODULES = ["biologics", "inventory", "labbook", "puppeteer", "recipe", "sampleManagement"]

    static String getGitUrl(Project project)
    {
        def grgit = Grgit.open(currentDir: project.projectDir)
        List<Remote> remotes = grgit.remote.list()
        grgit.close()
        if (remotes) {
            return remotes.get(0).url
        } else {
            return null
        }

    }

    static void setTomcatLibs(List<String> libs)
    {
        TOMCAT_LIBS = new ArrayList(libs)
    }

    static void addTomcatBuildDependencies(Project project, String configuration)
    {
        List<String> tomcatLibs = new ArrayList<>(TOMCAT_LIBS) // Don't modify list
        if (!"${project.apacheTomcatVersion}".startsWith("7."))
            tomcatLibs.replaceAll({it.replace('tomcat7-', 'tomcat-')})
        for (String lib : tomcatLibs)
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

    static void addModuleDistributionDependency(Project distributionProject, String depProjectPath, String config, boolean addTransitive)
    {
        if (distributionProject.configurations.findByName(config) == null)
            distributionProject.configurations {
                config
            }
        distributionProject.logger.info("${distributionProject.path}: adding ${depProjectPath} as dependency for config ${config}")
        addLabKeyDependency(project: distributionProject, config: config, depProjectPath: depProjectPath, depProjectConfig: "published", depExtension: "module", depVersion: distributionProject.labkeyVersion)
        if (addTransitive) {
            Set<String> pathsAdded = new HashSet<>()
            addTransitiveModuleDependencies(distributionProject, distributionProject.findProject(depProjectPath), config, pathsAdded)
        }

    }

    private static void addTransitiveModuleDependencies(Project distributionProject, Project depProject, String config, Set<String> pathsAdded)
    {
        if (depProject == null)
            return

        distributionProject.evaluationDependsOn(depProject.getPath())
        if (depProject.configurations.findByName("modules") != null) {
            depProject.configurations.modules.dependencies.each { dep ->
                if (dep instanceof ProjectDependency)
                {
                    if (!pathsAdded.contains(dep.getDependencyProject().getPath())) {
                        distributionProject.logger.info("${distributionProject.path}: Adding '${config}' dependency on project ${dep}")
                        distributionProject.dependencies.add(config, dep)
                        distributionProject.evaluationDependsOn(dep.getDependencyProject().getPath())
                        pathsAdded.add(dep.getDependencyProject().getPath())
                        distributionProject.logger.debug("${distributionProject.path}: Adding recursive '${config}' dependenices from ${dep.dependencyProject}")
                        addTransitiveModuleDependencies(distributionProject, dep.dependencyProject, config, pathsAdded)
                    }
                }
                else
                {
                    distributionProject.logger.info("${distributionProject.path}: Adding ${config} dependency on artifact ${dep}")
                    distributionProject.dependencies.add(config, dep)
                }
            }
        }
    }

    static void addModuleDistributionDependencies(Project distributionProject, List<String> depProjectPaths, boolean addTransitive)
    {
        addModuleDistributionDependencies(distributionProject, depProjectPaths, "distribution", addTransitive)
    }


    static void addModuleDistributionDependencies(Project distributionProject, List<String> depProjectPaths)
    {
        addModuleDistributionDependencies(distributionProject, depProjectPaths, "distribution", true)
    }

    static void addModuleDistributionDependencies(Project distributionProject, List<String> depProjectPaths, String config, boolean addTransitive)
    {
        depProjectPaths.each{
            String path -> addModuleDistributionDependency(distributionProject, path, config, addTransitive)
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

        if (depProject != null && shouldBuildFromSource(depProject) || shouldForceBuildFromSource(parentProject, depProjectPath))
        {
            if (depProject != null)
                parentProject.logger.debug("Found project ${depProjectPath}; building ${depProjectPath} from source")
            else
                parentProject.logger.debug("Did not find project ${depProjectPath}; forcing project dependency as requested by '-P${BUILD_FROM_SOURCE_PROP}'")

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
                    depVersion = parentProject.labkeyVersion
            }
            else
            {
                parentProject.logger.debug("${depProjectPath} project found but not building from source because: "
                        + whyNotBuildFromSource(parentProject, BUILD_FROM_SOURCE_PROP).join("; "))
                if (depVersion == null)
                    depVersion = depProject.labkeyVersion
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

    private static boolean shouldForceBuildFromSource(Project parentProject, String projectPath)
    {
        return BuildFromSource.fromProperty(parentProject, BUILD_FROM_SOURCE_PROP) == BuildFromSource.FORCE && projectPath.contains(":modules:")
    }

    static String getLabKeyArtifactName(Project parentProject, String projectPath, String version, String extension)
    {
        String moduleName
        String group = extension.equals("module") ? LabKeyExtension.LABKEY_MODULE_GROUP : LabKeyExtension.LABKEY_API_GROUP
        if (projectPath.endsWith(getRemoteApiProjectPath(parentProject.gradle).substring(1)))
        {
            group = LabKeyExtension.LABKEY_API_GROUP
            moduleName = "labkey-client-api"
        }
        else if (projectPath.equals(getBootstrapProjectPath(parentProject.gradle)))
        {
            group = LabKeyExtension.LABKEY_GROUP
            moduleName = BOOTSTRAP_JAR_BASE_NAME
        }
        else if (projectPath.equals(getTestProjectPath(parentProject.gradle)))
        {
            group = LabKeyExtension.LABKEY_API_GROUP
            moduleName = 'labkey-api-selenium'
        }
        else if (projectPath.equals(getEmbeddedProjectPath(parentProject.gradle)))
        {
            group = LabKeyExtension.LABKEY_BUILD_GROUP
            moduleName = 'embedded'
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

    static String getModuleProjectPath(ModuleDependency dependency)
    {
        if (LIMS_MODULES.contains(dependency.getName()))
            return ":server:modules:limsModules:" + dependency.getName()
        return ":server:modules:" + dependency.getName()
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
        int i = 0
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

    static String getEmbeddedConfigPath(Project project)
    {
        return new File(project.serverDeploy.embeddedDir, "config").absolutePath
    }

    static File getExecutableServerJar(Project project)
    {
        File deployDir = new File(ServerDeployExtension.getEmbeddedServerDeployDirectory(project))
        File[] jarFiles = deployDir.listFiles(new FilenameFilter() {
            @Override
            boolean accept(File dir, String name) {
                return name.endsWith("jar")
            }
        })
        if (jarFiles.size() == 0)
            return null
        else if (jarFiles.size() > 1)
            throw new GradleException("Found ${jarFiles.size()} jar files in ${deployDir}.")
        else
            return jarFiles[0]
    }

    static File getWebappConfigFile(Project project, String fileName)
    {
        if (project.rootProject.file("webapps/" + fileName).exists())
            return project.rootProject.fileTree("webapps/" + fileName).singleFile
        else if (project.rootProject.file("server/configs/webapps/" + fileName).exists())
            return project.rootProject.fileTree("server/configs/webapps/" + fileName).singleFile
        else
            return ModuleDistribution.getDistributionResources(project).matching {include fileName}.singleFile
    }

    static boolean embeddedProjectExists(Project project)
    {
        return project.findProject(getEmbeddedProjectPath(project.gradle)) != null
    }

    static boolean useEmbeddedTomcat(Project project)
    {
        _useEmbeddedTomcat(project)
    }

    static boolean useEmbeddedTomcat(Settings settings)
    {
        _useEmbeddedTomcat(settings)
    }

    private static boolean _useEmbeddedTomcat(Object o)
    {
        o.hasProperty(USE_EMBEDDED_TOMCAT) && o[USE_EMBEDDED_TOMCAT] != "false"
    }

    /**
     * Writes a file in the build/deploy/modules directory that can be used as a trigger file for restarting
     * SpringBoot. Without this, restarts may happen before the full application deployment is done, resulting
     * in a failed start. See
     * https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#using.devtools.restart.triggerfile
     * We use build/deploy/modules because when using a local build it's added in the application.properties file as a
     * spring.devtools.restart.additional-paths
     *
     * @param project - for use in getting the rootProject's build directory
     */
    static void updateRestartTriggerFile(Project project)
    {
        if (!project.hasProperty('useLocalBuild') || "false" == project.property("useLocalBuild"))
            return

        File triggerFileDir = project.rootProject.layout.buildDirectory.file("deploy/modules").get().getAsFile()
        if (!triggerFileDir.exists())
            return

        OutputStreamWriter writer = null
        try {
            File triggerFile = new File(triggerFileDir, RESTART_FILE_NAME)
            writer = new OutputStreamWriter(new FileOutputStream(triggerFile), StandardCharsets.UTF_8)
            writer.write(SimpleDateFormat.getDateTimeInstance().format(new Date()))
        }
        finally
        {
            if (writer != null)
                writer.close()
        }
    }

    static void addExternalDependency(Project project, ExternalDependency dependency, Closure closure=null)
    {
        project.dependencies.add(dependency.configuration, dependency.coordinates, closure)
        ModuleExtension extension = project.extensions.findByType(ModuleExtension.class)
        extension.addExternalDependency(dependency)
    }

    static void addExternalDependencies(Project project, List<ExternalDependency> dependencies)
    {
        dependencies.forEach({
            dependency -> addExternalDependency(project, dependency)
        })
    }

    static File getBuildDir(Project project)
    {
        return project.layout.buildDirectory.getAsFile().get()
    }

    static String getBuildDirPath(Project project)
    {
        return getBuildDir(project).path
    }

    static File getBuildDirFile(Project project, String filePath)
    {
        return project.layout.buildDirectory.file(filePath).get().asFile
    }

    static File getRootBuildDirFile(Project project, String filePath)
    {
        return project.rootProject.layout.buildDirectory.file(filePath).get().asFile
    }

    static String getRootBuildDirPath(Project project)
    {
        return project.rootProject.layout.buildDirectory.get().asFile.path
    }

    static void substituteModuleDependencies(Project project, String configName)
    {
        try {
            project.configurations.named(configName) { Configuration config ->
                resolutionStrategy.dependencySubstitution { DependencySubstitutions ds ->
                    project.rootProject.subprojects {
                        Project p ->
                            {
                                p.logger.debug("Considering substitution for ${p.path}.")
                                if (shouldBuildFromSource(p)) {
                                    if (p.plugins.hasPlugin('org.labkey.build.module') ||
                                            p.plugins.hasPlugin('org.labkey.build.fileModule') ||
                                            p.plugins.hasPlugin('org.labkey.build.javaModule')
                                    ) {
                                        ds.substitute ds.module("org.labkey.module:${p.name}") using ds.project(p.path)
                                        p.logger.debug("Substituting org.labkey.module:${p.name} with ${p.path}")
                                    }
//                                    if (p.plugins.hasPlugin('org.labkey.build.api'))
//                                    {
//                                        ds.substitute ds.module("org.labkey.api:${p.name}") using ds.project(p.path)
//                                    }
                                }
                            }
                    }
                }
            }
        } catch (UnknownDomainObjectException ignore) {
            project.logger.debug("No ${configName} configuration found for ${project.path}.")
        }
    }

    enum BuildFromSource {
        _TRUE,
        _FALSE,
        FORCE

        static BuildFromSource fromProperty(Project project, String propertyName) {
            String propertyValue = project.hasProperty(propertyName) ? project.property(propertyName) : null
            fromProperty(propertyValue)
        }

        static BuildFromSource fromProperty(String propertyValue) {
            if ('force' == propertyValue)
                return FORCE
            else
                return Boolean.valueOf(propertyValue) ? _TRUE : _FALSE
        }
    }
}
