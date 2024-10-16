
## gradlePlugin

The gradlePlugin jar is a jar file containing plugins, tasks, extensions and utilities used for building the [LabKey](https://www.labkey.org)
Server application and its modules.

If building your own LabKey module, you may choose to use these plugins or not. They bring in a lot of functionality 
but also make certain assumptions that you may not want to impose on your module. See the 
[LabKey documentation](https://www.labkey.org/Documentation/wiki-page.view?name=gradleModules) for more information.

If you are making changes to the plugins, please see the [internal docs](https://internal.labkey.com/Handbook/Dev/wiki-page.view?name=gradlePlugins) for more information
on how to do that, including how to develop and test locally and the versioning information.

## Release Notes

### 4.3.0-SNAPSHOT
*Released*: TBD
(Earliest compatible LabKey version: 24.11)
- Stop adding the standalone `VERSION` file to distribution archives; the `distribution.properties` file now
  contains the `version` and `buildUrl` properties that `EmbeddedExtractor.java` reads.

### 4.2.0
*Released*: 11 October 2024
(Earliest compatible LabKey version: 24.11)
- Stop adding the standalone `distribution` file to distribution archives; the `distribution.properties` file now
  contains the distribution name and other properties used by the webapp.
- Update all dependencies to the latest versions
- Add `version` and `buildUrl` to `distribution.properties` file

### 4.1.0
*Released*: 25 September 2024
(Earliest compatible LabKey version: 24.10)
- Update default value handling for `subDirName` and `extraFileIdentifier` properties: strip `"_dist"` suffix from `project.name` if present
- Eliminate "-embedded" suffix from distribution filenames
- Infer appropriate "default database" value based on JDBC driver name. Remove support for now unnecessary "databaseMaster" property.
- Add support for `extraProperties` map, which gets written to `WEB-INF/classes/distribution.properties` along with
  standard distribution build properties.
- Assign `group` inside `ModuleDistribution` task so build files no longer need to
- Add `startLabKey` and `stopLabKey` tasks that will eventually replace `startTomcat` and `stopTomcat`

### 4.0.0
*Released*: 5 September 2024
(Earliest compatible LabKey version: 24.10)
- Add `--add-opens` JVM arg for `java.nio` to `startTomcat` task. Required for Snowflake JDBC driver.
- Update `distributions` task to build embedded Tomcat tar.gz archives only
  - Remove support for building zip, Tomcat standalone, and module archives  
  - Remove `makeDistribution` and `embeddedArchiveType` properties
  - Fix `subDirName` property's optional behavior; this property now correctly defaults to `project.name`.
  - Change `extraFileIdentifier` property's default value to `-project.name`
- Remove support for `useEmbeddedTomcat` property and remove all standalone handling 
- Remove all `tomcat-lib` JAR file handling: `cleanTomcatLib`, `deployTomcatJars`, and `stageTomcatJars` tasks;
  `tomcatJars` configuration
- Remove `labkey.xml` and `log4j2.xml` files and handling
- Remove `distributionResources` folder

### 3.0.1
*Released*: 18 July 2024
(Earliest compatible LabKey version: 24.8)
- Fix issue with JSP copying because of missing input directory
- Add missing `onlyIf` condition for `WriteDependenciesFile`

### 3.0.0
*Released*: 15 July 2024
(Earliest compatible LabKey version: 24.8)
- Remove `AntBuild` plugin
- Updates for compatibility with configuration cache
  - `SchemaCompile`
  - `WriteDependenciesFile`
  - Most of the Jsp plugin, except where not supported from Gradle or not in usual build chain
  - `CheckForVersionConflicts` 
  - `ModuleXmlFile` task introduced for ad-hoc `moduleXml` task
- Remove support for `.jsp` files in module `resources` directories
- Remove support for `creditable` configuration for generating entries in `jars.txt` file
- Remove unused `Standalone` plugin for building stand-alone distributions

### 2.7.2
*Released*: 24 June 2024
(Earliest compatible LabKey version: 24.5)
* Remediate excessive disk space usage by `ModuleDistribution` tasks

### 2.7.1
*Released*: 12 June 2024
(Earliest compatible LabKey version: 24.5)
* Compatibility with Gradle 8.8 that doesn't want Strings and GStrings to be in the same list
* [Issue 50563](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=50563): Better management of versions of embedded jar

### 2.7.0
*Released*: 14 May 2024
(Earliest compatible LabKey version: 24.5)
* Include required reflection parameters to embedded `StartTomcat`
* Build embedded distribution archives regardless of `useEmbeddedTomcat` flag
* Add flags to override distributions' specified archive type (`forceStandaloneDist` & `forceEmbeddedDist`)

### 2.6.4
*Released*: 6 May 2024
(Earliest compatible LabKey version: 24.5)
* Put startup properties in correct folder for embedded deployments

### 2.6.3
*Released*: 12 April 2024
(Earliest compatible LabKey version: 24.5)
* Change `Distribution.inheritDependencies` exclusion list to be just the module names

### 2.6.2
*Released*: 6 April 2024
(Earliest compatible LabKey version: 24.3)
* Fix problem with `BuildUtils.getModuleProjectPath` to account for `limsModules` repo

### 2.6.1
*Released*: 12 March 2024
(Earliest compatible LabKey version: 24.2)
* Account for a missing directory for reloadTrigger file

### 2.6.0
*Released*: 11 March 2024
(Earliest compatible LabKey version: 24.2)
* Include `application.properties` in embedded distributions
* Remove version from executable server jar name: `labkeyServer.jar`
* Make `startTomcat` task work for embedded Tomcat on Windows

### 2.5.1
*Released*: 11 March 2024
(Earliest compatible LabKey version: 24.2)
* Don't write restartTrigger file if not using local build (e.g., on TeamCity)
* Avoid errors for missing `distributionDir` property

### 2.5.0
*Released*: 10 March 2024
(Earliest compatible LabKey version: 24.2)
* [Issue 49824](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=49824): Improve embedded Tomcat auto-redeploy using a trigger file

### 2.4.0
*Released*: 27 February 2024
(Earliest compatible LabKey version: 24.2)
* Make `-PbuildFromSource=force` force modules to be built from source
* Update `log4j2.xml` to match embedded tomcat version

### 2.3.0
*Released*: 19 February 2024
(Earliest compatible LabKey version: 24.2)
* `DoThenSetup` tasks: update property replacement for new jdbc config pattern
* `DoThenSetup` tasks: configure shutdown port for embedded tomcat
* Modify or skip some cleanup steps for embedded Tomcat
* `TeamCityExtension.writeStartupProperties`: Target correct directory for embedded distribution

### 2.2.2
*Released*: 31 January 2024
(Earliest compatible LabKey version: 24.2)
* Allow `useEmbeddedTomcat` and `useLocalBuild` to be turned off

### 2.2.1
*Released*: 22 January 2024
(Earliest compatible LabKey version: 24.2)
* Make `stopTomcat` and `killFirefox` tasks more reliable on TeamCity

### 2.1.1 - 2.2.0 accidentally skipped

### 2.1.0
*Released*: 12 January 2024
(Earliest compatible LabKey version: 24.2)
* Update coordinates for gwt dependencies to `org.gwtproject` instead of `com.google.gwt`

### 2.0.0
*Released*: 9 January 2024
(Earliest compatible LabKey version: 24.2)
* Remove redundant plugin identifiers using org.labkey namespace instead of org.labkey.build 
   (e.g., org.labkey.module removed in favor of org.labkey.build.module)
* Add support for using the net.nemerosa.versioning plugin directly instead of our forked version 
* Update dependency versions
* Add `substituteModuleDependecies` utility method for experimentation with some dependency substitution ([Issue 49316](https://www.labkey.org/home/Developer/issues/Secure/issues-details.view?issueId=49316))
* Remove support for `skipBuild` property
* Make `startTomcat` environment consistent between standalone and embedded Tomcat

### 1.44.2
*Released*: 4 January 2024
(Earliest compatible LabKey version: 23.3)
* Fix `symlinkNode` task to use proper propject

### 1.44.1
*Released*: 28 December 2023
(Earliest compatible LabKey version: 23.3)
* Prevent improper caching of `jsp2Java` task

### 1.44.0
*Released*: 19 December 2023
(Earliest compatible LabKey version: 23.3)
* Add syntax to uncomment certain properties in `application.properties` during deploy tasks
* Update example `log4j2.xml` configuration file
* Introduce [connection pool optimizations](https://github.com/LabKey/server/pull/304) to `labkey.xml`
* Add property `ignoreApiDep` for use when tracking down dependencies to avoid lots of duplication of dependenies from the api module
* Pass `useEmbeddedTomcat` flag to UI tests

### 1.43.1
*Released*: 16 November 2023
(Earliest compatible LabKey version: 23.3)
* Don't use providers for ant directories.

### 1.43.0
*Released*: 16 November 2023
(Earliest compatible LabKey version: 23.3)
* [Issue 49045](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=49045) - make copying to the modules-api directory a proper task, so it can run even if apiJar does not
* Eliminate usages of deprecated `project.buildDir`
* Update `BuildUtils.includeModules` to be able to specify full module paths for exclusion, not just directory names

### 1.42.2
*Released*: 26 October 2023
(Earliest compatible LabKey version: 23.3)
* Update `PurgeNpmAlphaVersions` task to handle a single version returned as a string instead of an array

### 1.42.1
*Released*: 11 October 2023
(Earliest compatible LabKey version: 23.3)
* Add `@labkey/ehr` to set of npm packages to purge versions for

### 1.42.0
*Released*: 5 October 2023
(Earliest compatible LabKey version: 23.3)
* Add RestoreFromTrash task to make it easier to restore artifact version from Artifactory
* Increase complexity of default TeamCity test password
* [Issue 48120](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=48120): Include xsd files in api jar files

### 1.41.2
*Released*: 24 July 2023
(Earliest compatible LabKey version: 23.3)
* Set DuplicatesStrategy for various copy tasks

### 1.41.1
*Released*: 28 June 2023
(Earliest compatible LabKey version: 23.3)
* Don't apply module version to LabKey dependencies
  * Allows correct dependency resolution when using `versioning` plugin

### 1.41.0
*Released*: 8 June 2023
(Earliest compatible LabKey version: 23.3)
* Add plugin for purging artifact versions from Artifactory
* Add property to limit NPM build task concurrency (`npmRunLimit`)
* Remove use of local repository for dependencies

### 1.40.6
*Released*: 15 May 2023
(Earliest compatible LabKey version: 23.3)
* Remove use of reserved configuration name for JSPs
* [Issue 47854: Improve audit trail of LabKey install script](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=47854)
* Allow more customization of embedded tomcat properties

### 1.40.5
*Released*: 23 March 2023
(Earliest compatible LabKey version: 23.3)
* Improve performance of Gradle task configuration

### 1.40.4
*Released*: 21 March 2023
(Earliest compatible LabKey version: 23.3)
* Fix check for minification project

### 1.40.3
*Released*: 15 March 2023
(Earliest compatible LabKey version: 23.3)
* [Issue 47465](https://www.labkey.org/home/Developer/issues/Secure/issues-details.view?issueId=47465): Check for the presence of the minification project before adding tasks related to it

### 1.40.2
*Released*: 6 March 2023
(Earliest compatible LabKey version: 23.3)
* Update dependency declarations for Gradle 8.0.1

### 1.40.1
*Released*: 27 February 2023
(Earliest compatible LabKey version: 23.3)
* Include feature branch name in `patchApiModule` output
  * [Issue 47370: Patched API module doesn't get named correctly on feature branches](https://www.labkey.org/home/Developer/issues/Secure/issues-details.view?issueId=47370)

### 1.40.0
*Released*: 8 February 2023
(Earliest compatible LabKey version: 23.3)
* Remove packages that are no longer published from `PurgeNpmAlphaVeresions` consideration
* Remove references to internal module and adjust dependency declarations accordingly
* Remove obsolete reference to labkey-api-sas project
* Remove option for using yui compressor
* Allow customizing database name on TeamCity

### 1.39.1
*Released*: 11 January 2023
(Earliest compatible LabKey version: 22.9)
* Correctly disable `npm` minifier if minification project is missing

### 1.39.0
*Released*: 10 January 2023
(Earliest compatible LabKey version: 22.9)
* Update getRemoteApiProjectPath() to match `labkey-api-java` layout refactor
* Upgrade a few dependencies

### 1.38.0
*Released*: 9 January 2023
(Earliest compatible LabKey version: 22.9)
* Update `ClientLibsCompress` task to use npm packages instead of the ancient YuiCompressor by default
  * Use [terser](https://github.com/terser/terser) for javascript minification 
  * Use [cssnano](https://cssnano.co/) via [postcss](https://github.com/postcss/postcss) for css minification
  * Add check for `useYuiCompressor` property on projects to enable deprecated functionality

### 1.37.1
*Released*: 9 December 2022
(Earliest compatible LabKey version: 22.9)
* Update package names for PurgeNpmAlphaVersions task to include @labkey/premium

### 1.37.0
*Released*: 8 December 2022
(Earliest compatible LabKey version: 22.9)
* Add `createStartupPropertyFile` task to define arbitrary LabKey startup properties
  * Controlled by `labkey.startup.properties` TeamCity property
* Add `includeDistModules` task to populate `ModuleLoader.include` startup property
  * Controlled by `labkey.startup.includeDistModules` TeamCity property

### 1.36.1
*Released*: 18 November 2022
(Earliest compatible LabKey version: 22.9)
* Check for the presence of the API project before adding task dependency to `patchApiModule`. Allows building distributions with a minimal enlistment.
* Make `patchApiModule` task more reliable

### 1.36.0
*Released*: 26 October 2022
(Earliest compatible LabKey version: 22.9)
* Don't use static variable when checking for modules with name overlaps
* When adding distribution dependencies, include all dependencies from the modules configuration for each project
* [Issue 46212](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=46212) Remove writing of dependencies.txt file

### 1.35.0
*Released*: 1 Sep 2022
(Earliest compatible LabKey version: 22.9)
* Update `javax.servlet-api` coordinates to match server

### 1.34.4
*Released*: 24 Aug 2022
(Earliest compatible LabKey version: 22.3)
* Fix `Distribution.inheritDependencies` so excluded modules work when not building from source
* Fix formatting for some generated `jars.txt` files

### 1.34.3
*Released*: 10 Aug 2022
(Earliest compatible LabKey version: 22.3)
* Ignore harmless exceptions from `TeamCity.ensureShutdown(..)`

### 1.34.2
*Released*: 28 July 2022
(Earliest compatible LabKey version: 22.3)
* Add `--add-opens` JVM args to test runners (removed in [Gradle 7.5](https://docs.gradle.org/7.5/userguide/upgrading_version_7.html#removes_implicit_add_opens_for_test_workers))

### 1.34.1
*Released*: 22 June 2022
(Earliest compatible LabKey version: 22.3)
* Add `pickJtds` task for configuring LabKey to use the jTDS SQL Server driver
* Update dependencies (since grgit has gone missing on mavenCentral)

### 1.33.1
*Released*: 9 June 2022
(Earliest compatible LabKey version: 22.3)
* Fix `Distribution.inheritDependencies` so the excluded modules parameter works (again)
* [Issue 45622](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=45622): Generated `jars.txt` files are encoded as 1252 (ANSI - Latin I) on Windows builds but read using UTF-8

### 1.33.0
*Released*: 24 May 2022
(Earliest compatible LabKey version: 22.3)

* Stop specifying `javasource` parameter when invoking XMLBeans build; server XMLBeans dependency has been upgraded to 5.0.3, which fails if this parameter is provided.
* Add `@labkey/eln` to the packages PurgeNpmAlphaVersions knows about
* Fix NPE-type error when output from `npm view` command is empty
* [Issue 44490](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=44490) Fix WriteDependenciesFile so it works with classifiers

### 1.32.2
*Released*: 11 February 2022
(Earliest compatible LabKey version: 22.2)
* Update inputs of `SchemaCompile` task, so it will defeat the cache when the xmlBeans version changes
* Update `RunUiTest` reports to non-deprecated properties
* Change `copyJspResources` to use `project.copy` instead of a `Copy` task to avoid warnings about cache optimizations that are not interesting

### 1.32.1
*Released*: 24 January 2022
(Earliest compatible LabKey version: 22.2)
* Include `testAutomation` resources in TestRunner sourceSets
* Set defaults for properties needed by `RunUiTest`
* [Issue 44600](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=44600) Support new 'EncryptionKey' property name
* Stop specifying `javasource` parameter when invoking XMLBeans build for version 5.0.0 or later; recent XMLBeans versions fail if this parameter is provided.

### 1.32.0
*Released*: 5 January 2022
(Earliest compatible LabKey version: 22.2)
* Add `--legacy-peer-deps` flag to `npmInstall` command.

### 1.31.0
*Released*: 15 December 2021
(Earliest compatible LabKey version: 22.1)
* Pick up the bootJar as a dependency for the embedded distribution, not the default jar

### 1.30.3
*Released*: 18 October 2021
(Earliest compatible LabKey version: 21.3)
* Don't cache the `fileModule` plugin's `moduleXml` task
* Adjust `test.properties.template` usage when running on TeamCity
* Fix extra data source configuration in DoThenSetup

### 1.30.2
*Released*: 29 September 2021
(Earliest compatible LabKey version: 21.3)
* Support `test.properties.template` file in `testAutomation` repository
* Allow DoThenSetup task to pull extra data source configuration from `config.properties`
* Stop deploying tomcat jars to `CATALINA_HOME` when it isn't necessary

### 1.30.1
*Released*: 7 September 2021
(Earliest compatible LabKey version: 21.3)
* Add `--no-multiarch` flag to `InstallRPackage` commands
* Update `PurgeNpmAlphaVersions` task to use `npm.cmd` on Windows

### 1.30.0
*Released*: 27 July 2021
(Earliest compatible LabKey version: 21.3)
* Add new PurgeNpmAlphaVersion task to make it easier to remove obsolete alpha package versions

### 1.29.0
*Released*: 7 July 2021
(Earliest compatible LabKey version: 21.3)
* Enable building embedded distributions from standalone modules
  * Requires `org.labkey.build:embedded` Available for 21.3.8+ and 21.7+ (not available for 21.4, 21.5, or 21.6)
* Remove irrelevant files from embedded distributions
* Issue 43356: manual-upgrade.sh script fails to upgrade standalone installer

### 1.28.1
*Released*: 24 June 2021
(Earliest compatible LabKey version: 21.3)
* Remove unneeded output directory declaration for undeployModule to get rid of lots of warnings

### 1.28.0
*Released*: 16 June 2021
(Earliest compatible LabKey version: 21.3)
* Add some constants helpful for declaring external dependencies
* Fix check for tomcat plugin so `cleanBuild` and `cleanDeploy` will stop tomcat if available
* Update to gradle 7.1
* Change from deprecated javaExec.main to javaExec.mainClass

### 1.27.0
*Released*: 26 May 2021
(Earliest compatible LabKey version: 21.3)
* [Issue 43207](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=43207): When building a file module, we don't want to create a jar file because that prevents the module from being used in the module loader
* Allow modules that utilize LabKey JSP tags to build without platform repository present
* Add `standalone` plugin to enable building distributions without `server` repository
  * Requires module to be configured for a [standalone build](https://www.labkey.org/Documentation/wiki-page.view?name=gradleModules)
  * Requires `org.labkey.build:tomcat-libs` artifact; available for LabKey version 21.3.5+ and 21.6+ (it is not available for 21.4 or 21.5)

### 1.26.0
*Released*: 9 April 2021
(Earliest compatible LabKey version: 21.3)
* Update GWT plugin to remove deprecated gwtCompile configuration
* Update NpmRun plugin to declare a more generic gen directory as output
* Use default `libs` output directory for jar files and put external libs into separate directory
  before copying to `explodedModule/libs` for better Gradle optimization
* (incubating) Add ability to generate jars.txt from the build 
  * Add `BuildUtils.addExternalDependency` method for declaring dependencies to be included in the `jars.txt` file
  * Modify `WriteDependenciesFile` to also write a new jars.txt file if `addExternalDependency` has been used

### 1.25.1
*Released*: 15 February 2021
(Earliest compatible LabKey version: 21.3)
* Add `build.gradle` file as input to `moduleXml` task

### 1.25.0
*Released*: 8 February 2021
(Earliest compatible LabKey version: 21.3)
* Remove support for ModuleDependencies in module.properties file
* Add `checkModuleTasks` task that is in the `deployApp` dependencies chain and will warn if a modules i found that has a `module.properties` file but no `module` task.
* Replace `jcenter`, which is going away, with `mavenCentral`, which is not

### 1.24.3
*Released*: 3 February 2021
(Earliest compatible LabKey version: 21.1)
* Fix infinite loop in `findLicensingProject`

### 1.24.2
*Released*: 2 February 2021
(Earliest compatible LabKey version: 21.1)
* Separate `patchApiModule` task, its dependencies and configurations to a new plugin that can be applied where needed 

### 1.24.1
*Released*: 26 January 2021
(Earliest compatible LabKey version: 21.1)
* Fix Distribution patchApiModule task to work when not building the api module 
* Fix gatherModules to use the proper configuration

### 1.24.0
*Released*: 21 January 2021
(Earliest compatible LabKey version: 21.1)
* [Issue 42227](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=42227) 
  Don't automatically clean the embedded deploy directory since it won't get copied to if the deployApp task is otherwise up-to-date
* Enable configuring an additional data source when deploying on TeamCity 
* Add check for isOpenSource project property in Distribution plugin to support packaging differently licensed libraries 
* [Issue 42187](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=42187) 
  Throw exceptions if trying to publish artifacts produced in dev mode or create distributions in dev mode. 

### version 1.23.0
*Released*: 7 January 2021
(Earliest compatible LabKey version: 21.1)
* Add `build.gradle` file to module created with `createModule` task.
* Fix relative path input for `createModule` so it is relative to the current directory not the gradle Daemon
* Add support for using embedded tomcat when the `useEmbeddedTomcat` property is set
    * Add ability to create distributions that contain an embedded tomcat distribution 
      by setting a new embeddedArchiveType property on the ModuleDistribution task
    * Adjust `pickDb` tasks to copy and filter an `application.properties` file into `build/deploy/embedded/configs` 
      instead of copying `labkey.xml` into the Tomcat configs directory.
    * Adjust deployApp, startTomcat, stopTomcat, and deployDistribution tasks to use the embedded installation 
      if the `useEmbeddedTomcat` property is set.
    * Add a `cleanEmbeddedDeploy` task for targeted cleaning of the `build/deploy/embedded` directory.
* Remove `includeWarArchive` property for distributions

### version 1.22.0
*Released*: 7 December 2020
(Earliest compatible LabKey version: 21.1)
* [Issue 41833](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=41833) Fix handling of modules that are
not meant to be built, either because they lack a module.properties file or because the skipBuild property is set.
* Remove support for skipbuild.txt file
* Change class structure for module plugins so we do not apply the plugins more than once.
* Update plugin publishing to use java-gradle-plugin, enabling use of modern plugin DSL when applying individual plugins
* Apply the java and base plugins within the plugins that depend on it
* Remove CoreScripts plugin in favor of defining the single task within the core module build.gradle
* Use an independent output directory for GWT compilation so the task can more reliably be cached
* Apply versioning plugin withing base plugin
* [Issue 41986](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=41986) Remove processing of clientapi/core
JS scripts. These no longer require script concatenation as the full scripts are now checked in to the server side scripts directory.

### version 1.21.2
*Released*: 23 November 2020
(Earliest compatible LabKey version: 20.9)
* Fix distribution filenames; use '-' to separate version from build number
* Include unique TeamCity build ID in module.xml properties

### version 1.21.1
*Released*: 5 November 2020
(Earliest compatible LabKey version: 20.9)
* Resolve the tomcatJars configuration before adding files to the ant task that copies them

### version 1.21.0
*Released*: 4 November 2020
(Earliest compatible LabKey version: 20.9)
* Designate certain tasks as cacheable for more efficient builds
* Use default locations, not in the build directory, for NpmRun tasks for more efficient builds
* Remove InstallRLabkey and InstallRuminex tasks as they are defined separately where used for tests

### version 1.20.0
*Released*: 22 October 2020
(Earliest compatible LabKey version: 20.9)
* Fix distribution and module versioning to work with Git server repository
* Add warnings about unsupported Version and ConsolidateScripts properties when found in module.properties files

### version 1.19.0
*Released*: 14 October 2020
(Earliest compatible LabKey version: 20.9)
* Make sure we don't duplicate the .module suffix in the groupId for modules dependencies
* Prepare for relocation of webapps directory under server/configs/webapps
* Make external config extend from runtimeOnly so we pick up those jars in the module's lib directory as well.
* Add TeamCity property to allow LabKey to load resources from module source
* Officially Deprecate the use of `ModuleDependencies` in the `module.properties` file

### version 1.18.0
*Release*: 17 September 2020
(Earliest compatible LabKey version: 20.9)
* Fix typo in module template controller class
* Remove duplicate jars in war file artifact
* Allow for module dependencies to come from different groups
* Support use of JSP Fragment (`.jspf`) files in WEB-INF directories for JSP compilation
* Convert ModuleResources and ClientLibraries from plugins to helper classes (for efficiency)

### version 1.17.1
*Released*: 13 September 2020
(Earliest compatible LabKey version: 20.9)
* Add explicit dependency between the process*Resources tasks and the npmRun task since we may produce *.lib.xml files
from npmRun now
* When finding lib.xml files to compress, don't use a cached map of these files since they may now be being created by npm

### version 1.17.0
*Released*: 10 September 2020
(Earliest compatible LabKey version: 20.9)
* Remove gradlePlugin type (no longer included under regular enlistment) and update module paths in comments
* Adjust moduleTemplate for removal of appendNavTrail in favor of addNavTrail
* Add missing dependency version numbers for some base module dependencies
* Parameterize path for server module project and config project (allowing for them to be separate)
* Remove additional assumptions on presence of server modules project when not needed
* Update configurations in JavaModule plugin to no longer reference the compile configuration (being deprecated for dependency resolution)

### version 1.16.0
*Released*: 26 August 2020
(Earliest compatible LabKey version: 20.9)
* Wait for Tomcat to shutdown gracefully before killing VM directly
* Allow test server credentials to be customized on TeamCity
* Update default project paths for current reality
* Update `deployModule` task to also copy in the modules that are required
* Fix `undeployModule` so it actually does something
* Update logic for getting LabKey artifact name to account for testAutomation artifact
* Move dependency declarations out of TestRunner plugin in favor of `testAutomation/build.gradle` declarations 
* Remove `testJar` task in favor of a default jar task declared in `testAutomatioin/build.gradle`
* Fix input and output declarations for `npmInstall`
 

### version 1.15.0
*Released*: 15 July 2020
(Earliest compatible LabKey version: 20.7)
* Allow for use of `logj2.xml` file based on presence of the property `log4j2Version`
* Add `CopyAndInstallRPackage` task for installing R packages from local source

### version 1.14.0
*Released*: 8 July 2020
(Earliest compatible LabKey version: 20.7)
* Remove use of deprecated `maven` plugin (in favor of `maven-publish`)
* Fix problem with copying from external directory that gets populated by TeamCity even after removed from SVN
* Remove no-longer-used ServerBootstrap plugin
* Update path in StartTomcat to not reference `external/windows/core`

### version 1.13.0
*Released*: 24 June 2020
(Earliest compatible LabKey version: 20.7)
* Remove ClientApiDistribution task no longer used in client-api distribution
* Increase default Tomcat heap to 2GB
* Check if api project exists before depending on it
* Remove deprecated apiCompile configuration from Api plugin
* [Issue 40668](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=40668) Add property to use build-prod even if in dev mode
* Adjust deployApp and distribution tasks to pull utility and proteomics binaries from Artifactory
* Remove `includeMassSpecBinaries` property from Distribution configuration (available for download From Artifactory)


### version 1.12.2
*Released*: 25 May 2020
(Earliest compatible LabKey version: 20.6)
* Remove assumption that GXT and GWT-DND are part of the build

### version 1.12.1
*Released*: 20 May 2020
(Earliest compatible LabKey version: 20.4)
* [Issue 40472](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=40472) Revert change in getLabKeyArtifactName for determining the group.
We will always use a LabKey group here.

### version 1.12.0
*Released*: 14 May 2020
(Earliest compatible LabKey version: 20.4)
* Eliminate duplication of jsp jars in war files
* Gradle 7 deprecation updates
* Disable loading module resources from source on TeamCity
* Restore use of module properties for generating pom files
* Add PomFileHelper utility to prepare for removal of deprecated maven plugin (in favor of maven-publish)
* Use project's group as a prefix to determine the group for api and module artifacts
* Make xsdDocZip  and jsDocZip tasks for publishing the doc files

### version 1.11.0
*Released*: 20 April 2020
(Earliest compatible LabKey version: 20.4)
* Removed RPackages plugin in favor of defining relevant tasks in RPackages' own `build.gradle` file
* Check for existence of `node_modules` directory before attempting to remove it with `cleanNodeModules` to avoid crankiness on Windows
* Restore input and output declarations for npmInstall as a possible fix for [Issue 40153](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=40153)
* Remove remoteapi/java as a base module
* Change default npmInstall command to `ci` instead of `install` with a property `npmInstallCommand`
that can be used to override this default (e.g., `PnpmInstallCommand=install`)
* Add utility methods for getting path to sas and jdbc api projects
* [Issue 40160](https://www.labkey.org/home/Developer/issues/Secure/issues-details.view?issueId=40160) Fix war distribution to include missing jar files.

### version 1.10.7
*Released*: 11 June 2020 
(Earliest compatible LabKey version: 20.3)
            
* Do not add projects to dedupe configuration dependency if they have buildFromSource=false

### version 1.10.6
*Released*: 19 May 2019
(Earliest compatible LabKey version: 20.3)

* [Issue 40472](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=40472) Revert change in getLabKeyArtifactName for determining the group.
 We will always use a LabKey group here.

### version 1.10.5
*Released*: 15 May 2020
(Earliest compatible LabKey version: 20.3)

* Fix Pom file generation to use license info from module.properties and project group as prefix for publish group of api and module artifacts

### version 1.10.4
*Released*: 14 April 2020
(Earliest compatible LabKey version: 20.3)
* Fix manual-upgrade.sh script since sh shell does not support arrays

### version 1.10.3
*Released*: 30 March 2020
(Earliest compatible LabKey version: 20.3)
* [Issue 40014](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=40014) Auto-delete JDBC drivers from \<tomcat\>/lib folder during setup, pickPg, pickMSSQL, et al tasks

### version 1.10.2
*Released*: 23 March 2020
(Earliest compatible LabKey version: 20.3)
* Remove more deprecated properties
* Fix problem with cleanNodeModules task dependency when using yarn instead of npm
* Let test harness locate WebDriver binaries
* Pass along all 'webtest' and 'webdriver' properties from TeamCity
* Don't lock Gradle daemon to a particular Tomcat version
* Catch exception and log error instead of throwing if gitPull has conflicts
* undeployModules adjusted to handle the new naming of module directories created by ModuleLoader
* [Issue 39643](https://www.labkey.org/home/Developer/issues/Secure/issues-details.view?issueId=39643) Remove some extraneous zip files

### version 1.10.1
*Released*: 27 February 2020
(Earliest compatible LabKey version: 20.3)
* Fix pom file generation for labkey-client-api inclusion
* [Issue 39722](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=39722) Make sure locally built modules aer 
preferred over externally build modules by using two different copy tasks. Two collections in one copy task seem to have
some randomness in their ordering. 
* Don't add npm tasks that rely on a package.json if there is no package.json file

## version 1.10.0
*Released*: 15 February 2020
(Earliest compatible LabKey version: 20.3)

* Better fix for [Issue 39058](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=39058). Depend on api's jar task, not just schemaCompile
* Make sure we run npm clean before cleanNodeModules so we don't reinstall node_modules in order to do the cleaning
* Add dedupe configuration for safe resolution of external configuration used for copying and deduplicating external jar dependencies
* Promote most multiGit tasks as non-incubating
* [Issue 39544](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=39544) Remove closure usage for DoThenSetup that would cause the "do" function to run twice.
* Exclude "webpack" directory from potential modules
* Update module.xml writer for version property changes 
  * Rename "version" property to "schemaVersion" and support null value
  * Rename "labkeyVersion" property to "releaseVersion"
  * Remove "consolidateScripts" property
* Update module template to match versioning changes
* Add apiJarFile configuration to replace apiCompile for dependencies on the api jar file
* use labkeyClientApiVersion property if available for declaring dependency on labkey-client-api
* Add BuildUtils.addBaseModuleDependencies to facilitate deploying a local server without building the base modules from source

### version 1.9.2
*Released*: 21 January 2020
(Earliest compatible LabKey version: 20.2)

* Remove obsolete 'jars' configuration
* Remove unused 'zipWebDir' task
* [Issue 39422](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=39422) Prevent duplicate class files in jars

### version 1.9.1
*Released*: 13 January 2020 
(Earliest compatible LabKey version: 20.2)

No functional changes; built with JDK 12 instead of 13

## version 1.9.0
*Released*: 12 January 2020
(Earliest compatible LabKey version: 20.2)

* Provide credentials for all multigit commands
* Remove announcements from base modules
* [Issue 39105](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=39105): Remove createApiFilesList task
* Remove unnecessary copying of the bootstrap jar, previously needed for the Windows installer
* [Issue 39058](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=39058): Make sure api's schemaCompile task precedes the schemaCompile task for other modules
* [Issue 38600](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=38600): Remove stageJars task and relatives
* Add missing Input and Output annotations for tasks

### version 1.8.4
*Released*: 19 May 2020
(Earliest compatible LabKey version: 19.3)

* [Issue 40472](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=40472) Revert change in getLabKeyArtifactName for determining the group.
 We will always use a LabKey group here.

### version 1.8.3
*Released*: 15 May 2020
(Earliest compatible LabKey version: 19.3)

* Fix Pom file generation to use license info from module.properties and project group as prefix for publish group of api and module artifacts

### version 1.8.2
*Released*: 29 Oct 2019
(Earliest compatible LabKey version: 19.3)

* Fix stageModules task so locally built modules will replace externally built modules that come through transitive dependencies

### version 1.8.1
*Released*: 21 Oct 2019
(Earliest compatible LabKey version: 19.3)

* Fix classpath for XSD schema compilation to prevent duplicate classes
* Remove .java files generated from XSD files from jar file
* Move to new node plugin that works with Gradle 6

## version 1.8
*Released*: 17 Oct 2019
(Earliest compatible LabKey version: 19.3)

* Update reallyClean to depend on cleanSchemasCompile and thus remove the classes generated from xsd files
* Remove the schemas jar, incorporating the schema classes into the "main" (implementation) jar.
* Change publications to publish only the api jar and .module file
* [Issue 38550](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=38550): Add authentication for fetching from repositories
* Create task to publish module pom files to artifactory and harness transitive module dependencies
* [Issue 38553](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=38553): Add labkey version to module.xml file for each module
* [Issue 38426](https://www.labkey.org/home/Developer/issues/Secure/issues-details.view?issueId=38426): MultiGit Gradle plugin recognizes forked repositories
* Remove support for pulling build dependencies from `CATALINA_HOME`
* Add property to allow TeamCity to run Tomcat under a different JDK `teamcity["tomcatJavaHome"]`

## version 1.7.0
*Released*: 27 Aug 2019
(Earliest compatible LabKey version: 19.1)

* Don't use yarn if there is a package-lock.json file.
* Add listNodeProjects task for showing which projects are using node in their build and which package manager
* Add links for both yarn and npm binaries
* [Issue 38198](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=38198): Find the nearest vcs root to use for populating vcs properties to accommodate modules nested in git repositories
* Modify incubating multi-git tasks to account for git modules not in optionalModules and testAutomation repo
* Add more incubating multi-git tasks: gitPull, gitPush, and gitStatus 
* Update reallyClean to depend on cleanNodeModules and thus also remove the node_modules directory for a module

### version 1.6.2
*Released*: 25 Jun 2019
(Earliest compatible LabKey version: 19.1)

* Fix for symlinkNode task (referencing unknown property)

### version 1.6.1
*Released*: 18 Jun 2019
(Earliest compatible LabKey version: 19.1)

* [Issue 37754](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=37754) - fix line endings for `manual-upgrade.sh`


## version 1.6
*Released*: 14 Jun 2019
(Earliest compatible LabKey version: 19.1)

* Update tasks for yarn support
* [Issue 36138](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=36138) - Remove compile-time dependency on local tomcat installation
  * Only require `tomcat.home`/`CATALINA_HOME` for tasks that use them

## version 1.5
*Released*: 23 May 2019
(Earliest compatible LabKey version: 19.1)

* [Issue 36138](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=36138) - Remove compile-time dependency on local tomcat installation 
* Include module containers with `BuildUtils.includeModules` if specified
* [Issue 37055](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=37055) - Deprecate pipeline configuration distribution type
* [Issue 36814](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=36814) - Move distribution resources out of server directory
* [Issue 37308](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=37308) - Make sure published pom file contains any classifiers in the verison number
* Update ModuleFinder.isModuleContainer method to look for a property on the project that would desginate it as a container.
* Allow primary uiTest project to exist somewhere other than `server/test`
* (Incubating) Enable yarn for NPM plugin configuration if yarnVersion and yarnWorkDirectory properties are defined 

### version 1.4.4
*Released*: 27 Mar 2019
(Earliest compatible LabKey version: 19.1)

* Build with Java 11 instead of 12

### version 1.4.3
*Released*: 25 Mar 2019
(Earliest compatible LabKey version: 19.1)

* Fix evaluation ordering for SpringConfig plugin when doing IntelliJ gradle syncing.

### version 1.4.2
*Released*: 20 Mar 2019
(Earliest compatible LabKey version: 19.1)

* Add ability to get base modules from a different location using the `platformProjectPath` property to
support some reorganization of the modules into git repositories.

### version 1.4.1
*Released*: 20 Feb 2019
(Earliest compatible LabKey version: 19.1)

* Remove Windows installer
* Update naming for alpha branches (formerly known as sprint branches)
* Convert to non-deprecated properties for artifact-producing tasks 
* (incubating) Add task for listing pull request info on a set of git repositories

## version 1.4
*Released*: 17 Jan 2019
(Earliest compatible LabKey version: 19.1)

* [Issue 36261](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=36261) - always include source path as module property
* Remove obsolete tools.jar from TestRunner dependencies
* Use implementation and runtimeOnly configurations for declaring dependencies instead of deprecated 'compile' dependency
* Rename task for running module UI tests to moduleUiTests since replacing tasks, as we were doing for the uiTest task in TaskRunner, is 
deprecated functionality.
* Remove obsolete 'local' configuration 
* [Issue 36309](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=36309) - make output directory for jsp2Java class explicit
* Start using [task configuration avoidance](https://blog.gradle.org/preview-avoiding-task-configuration-time) to improve configuration time for tasks
* Add property to run UI tests with additional JVM options (`uiTestJvmOpts`)
* No longer throw exception if a `modules` configuration dependency is declared for a project in the settings file but there is no enlistment for that project

### version 1.3.8
*Released*: 14 Jan 2019
(Earliest compatible LabKey version: 18.3)

* [Issue 36527](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=36527) - Don't add branch name to artifact version if on release snapshot branch

### version 1.3.7
(skipped)

### version 1.3.6
*Released*: 28 Nov 2018
(Earliest compatible LabKey version: 18.3)

* Move ThreadDumpAndKill functionality into TeamCity plugin, where it is used
* Change schemaCompile task to target Java 1.8 instead of project sourceCompatibility property to avoid warnings
* [Issue 36034](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=36034) - eliminate copying of api jar into WEB-INF/lib
* [Issue 35902](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=35902) - make undeployModule work for file-based modules
* Add 'extraCatalinaOpts' property to pass additional startup options to Tomcat
* [Issue 36171](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=36171) - make the contents of the .tar.gz and .zip distributions identical

### version 1.3.5
*Released*: 29 Oct 2018
(Earliest compatible LabKey version: 18.3)

* When copying the labkey.xml file to the tomcat conf directory, don't throw an exception if the directory does not exist but the user can create it (via the copy)
* Add VcsBranch and VcsTag to module properties xml file
* Handle alpha branch names for naming distributions (releaseX.Y.Z-alpha.W)
* Properly translate artifact versions with a patch version (X.Y.Z) to LabKey module version (X.Y)
* Removed TestRunner dependency on sardine.

### version 1.3.4
*Released*: 29 Oct 2018
(Earliest compatible LabKey version: 18.2)

(Accidental no-change release)

### version 1.3.3
*Released*: 18 Oct 2018
(Earliest compatible LabKey version: 18.2)

* add ability to enable ldap sync configuration in labkey.xml with -PenableLdapSync. This will uncomment a stanza in the labkey.xml
that is surrounded by &lt;`--@@ldapSyncConfig@@` and `@@ldapSyncConfig@@`--&gt;
* [Issue 35442](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=35442) - throw exception if tomcat directory is not present or not writeable when copying jars there
* add descriptions for our custom configurations (for use in task that shows all configurations)
* update to allow schemas project to be merged into the api project
* update pattern for jar checking to account for words in the release version (e.g., Spring's 4.3.0.RELEASE)

### version 1.3.2
*Released*: 29 Aug 2019
(Earliest compatible LabKey version: 18.2)

* [Issue 34390](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=34523) - make createModule use lower case name
for directory name (to correspond to package name)
* Clean up geckdriver processes on TeamCity (Selenium 3 support)
* Add new labkey configuration for use in declaring dependencies that do not need to be in the jars.txt file
* [Issue 35207](https://www.labkey.org/Rochester/support%20tickets/issues-details.view?issueId=35207) - make 
linking to npm executables work when not building from source
* Update template for createModule task to parameterize version number and copyright year

### version 1.3.1
*Releasedd*: 19 June 2018
(Earliest compatible LabKey version: 18.2)

* Remove code that attempted (but failed) to create symbolic links to node and npm directories on Windows. 

## version 1.3
*Released*: 18 June 2018
(Earliest compatible LabKey version: 18.2)

* [Issue 34523](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=34523) - Change configuration for NPM plugin to download
specific versions of node and npm if appropriate properties are set
* Added cleanNodeModules task that will remove a project's node_modules directory
* Change JavaModule plugin to remove ```src``` as a resource directory by default. Individual modules can declare it as a resource if needed.
* Parameterize gwt build so you can choose the target permutation browser in dev mode (using property gwtBrowser)
* [Issue 33473](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=33473) - always overwrite tomcat
lib jars to facilitate switching between newer and older versions of LabKey distributions
* [Issue 34388](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=34388) - clean out directories
created when compiling xsd's to jar file if a new jar is to be created.
* Update tasks that check version conflicts for jars and modules (no longer incubating). By default, the build will fail if version conflicts
are found. See the documentation on [Version Conflicts in Local Builds](https://labkey.org/Documentation/wiki-page.view?name=gradleDepend) for more information. 
* [Issue 33858](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=33858) add checks for the 
existence of ```internal/gwtsrc``` so we can move it to its proper home in api.
* Parameterize the location of some of the key, non-standard modules to make them easier to move around. Parameter are ```apiProjectPath```,
```bootstrapProjectPath```, ```internalProjectPath```, ```remoteapiProjectPath```, ```schemasProjectPath```, ```coreProjectPath```. These parameters are attached to the Gradle extension in the ```settings.gradle``` file (via the ```gradle/settings/parameters.gradle``` file).
* [Issue 33860](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=33860) - parameterization to 
allow for moving or removing :schemas project. Parameter is ```schemasProjectPath``` attached to the Gradle extension in the 
```settings.gradle``` file.
* [Issue 30536](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=30536) - copy moduleTemplate into
gradle plugins repository and modify build of plugins jar to include a zip of the moduleTemplate (that will include
the empty directories that won't migrate to git). Actual removal of moduleTemplate will not happen until LabKey 18.3.

### version 1.2.8
*Released*: 11 June 2018
(Earliest compatible LabKey version: 18.2)

* added TeamCity parameter testValidationOnly for test that will do validation only (e.g. upgrade tests, blue-green)
* dropDatabase will not happen if testValidationOnly is true
* include manual-upgrade.sh script in zip distributions

 
### version 1.2.7
*Released*: 23 May 2018
(Earliest compatible LabKey version: 18.2)

* update ClientApiDistribution to include the new jdbc jar file
* enable multiple worker threads for the GWT compile

### version 1.2.6
*Released*: 7 May 2018
(Earliest compatible LabKey version: 18.2)

* update TeamCity plugin to get labkey.server from teamcity properties if available
* update Gwt plugin to support later versions of gxt (artifact group name changed)
* make it possible to remove obsolete chromextensions directory
* [Issue 34078](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=34078) - update destination
directory for ajc compiler to reflect language-specific classes directories in Gradle 4+

### version 1.2.5
*Released*: 5 April 2018
(Earliest compatible LabKey version: 18.1)

* Slight refactor of test runner classes to void stack overflow with Gradle > 4.3.1
* Make killChrome on Windows kill chrome as well as chromedriver
* [Issue 32153](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=32153) (again) - 
don't read db properties from existing file when configuring UITest run as it may differ from what is chosen
by the TeamCity properties
* [Issue 33793](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=33793) - (incubating feature)
Add tasks to check for conflicting version numbers of jars created by the current build and those already in place in 
destination directories. See the issue for details on the tasks and the properties available to enable these tasks.

### version 1.2.4
*Released*: 8 Mar 2018
(Earliest compatible LabKey version: 18.1)

* [Issue 32420](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=32420) - another attempt to fix the log4j.xml file not getting updated with developer mode settings
* Update evaluation dependencies for distribution projects (in anticipation of moving these projects)
* Avoid infinite recursion for Gradle 4.5+ by removing call to setSystemProperties in constructor for RunTestSuite()
* [Issue 32874](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=32874) - make files in each module's `test/resources`
directory available for tests by including them in the :server:test:uiTest resources source directories
* [Issue 33389](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=33389) - use addLabKeyDependency to declare dependency
on api and internal for apiCompile so we respect the buildFromSource parameter

### version 1.2.3
*Released*: 17 Jan 2018
(Earliest compatible LabKey version: 18.1)
This version introduces some changes that are not compatible with Gradle versions before 4.x, so it will not be
compatible with older versions of LabKey.

* [Issue 32420](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=32420) - log4j.xml file not getting updated with developer mode settings
* [Issue 32290](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=32290) - add dependency on npmClean from module's clean task so all files built by npm
are removed when the module is cleaned. (Note that this does **not** affect the `node_modules` directory)
* Failure to stop tomcat should not cause failure when running tests in TeamCity
* [Issue 32413](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=32413) - get rid of some warnings about deprecated
methods that are to be removed with Gradle 5.0.
* When inheriting dependencies for a distribution, be sure to inherit even if the project is not included in the settings file
* [Issue 31917](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31917) - (incubating feature) Allow
module dependencies to be declared in the build.gradle file instead of module.properties
* [Issue 32153](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=32153) - look for the database type in TeamCity and project properties since the pickDb task may not have run yet
* [Issue 32730](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=32730) - make test jar an artifact of the test project to enable running uiTest task on individual modules

### version 1.2.2
*Released*: 13 Nov 2017
(Earliest compatible LabKey version: 17.2)

* FileModule plugin enforces unique names for LabKey modules
* [Issue 31985](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31985) - bootstrap task not connecting to master database for dropping database
* Update npm run tasks to use isDevMode instead of separate property to determine which build task to run
* [Issue 32006](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=32006) - update up-to-date check for npmInstall so it doesn't traverse
the entire node_modules tree (and stumble on broken symlinks); add package-lock.json file as input to npmInstall if it exists
* Use more standard up-to-date check for moduleXml task by declaring inputs and outputs
* Update some source set configuration to be more standard
* Make treatment of missing deployMode property consistent (default to dev mode)

### version 1.2.1
*Released*: 16 Oct 2017
(Earliest compatible LabKey version: 17.2)

* [Issue 31742](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31742) - Remove redundant npm_setup command for better performance
* [Issue 31778](https://www.labkey.org/home/Developer/issues/Secure/issues-details.view?issueId=31778) - Update jar and module naming for sprint branches
* [Issue 31165](https://www.labkey.org/home/Developer/issues/Secure/issues-details.view?issueId=31165) - Update naming convention for distribution files
* Update logic for finding source directory for compressClientLibs to use lastIndexOf "web" or "webapp" directory
* Exclude node_modules directory when checking for .lib.xml files for minor performance improvement

## version 1.2
*Released*: 28 Sept 2017
(Earliest compatible LabKey version: 17.2)

* [Issue 31186](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31186) - createModule task
should not copy scripts and schema.xml when hasManagedSchema == false
* [Issue 31390](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31390) - add `external/<os>` as 
an input directory for deployApp so it recognizes when new files are added and need to be deployed
* [Issue 30206](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=30206) - don't re-copy `labkey.xml`
if there have been no changes to database properties or context
* [Issue 31165](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31165) - update naming of distribution
files
* [Issue 31477](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31477) - add explicit task dependency
so jar file is included in client API Jar file
* [Issue 31490](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31490) - remove jar file from modules-api
directory when doing clean task for module
* [Issue 31061](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31061) - do not include jar files
 in module lib directories if already included in one of the base modules
* Improve cleaning for distribution tasks
* Make stageModules first delete the staging modules directory (to prevent picking up modules not in the current set) 
* Make cleanDeploy also cleanStaging
* Prevent creation of jar file if there is no src directory for a project
* Make sure jsp directory exists before trying to delete files from it
* remove npm_prune as a dependency on npmInstall
* add `cleanOut` task to remove the `out` directory created by IntelliJ builds
* collect R install logs into file
* enable passing database properties through TeamCity configuration
* add `showDiscrepancies` task to produce a report of all external jars that have multiple versions in the build

## version 1.1

*Released*: 3 August 2017
(Earliest compatible LabKey version: 17.2)

* [Issue 31046](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31046) - Remove JSP jars from WEB-INF/jsp directory with undeployModule
* [Issue 31044](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31044) - Exclude 
'out' directory generated by IntellijBuilds when finding input files for Antlr
* [Issue 30916](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=30916) - Prevent duplicate bootstrap 
jar files due to including branch names in version numbers

## version 1.0.1

*Released*: 2 July 2017
(Earliest compatible LabKey version: 17.2)

The first official release of the plugin to support Labkey 17.2 release.

