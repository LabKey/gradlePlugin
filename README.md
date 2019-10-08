## gradlePlugin

The gradlePlugin jar is a jar file containing plugins, tasks, extensions and utilities used for building the [LabKey](https://www.labkey.org)
Server application and its modules.

If building your own LabKey module, you may choose to use these plugins or not.  They bring in a lot of functionality 
but also make certain assumptions that you may not want to impose on your module.  See the 
[LabKey documentation](https://www.labkey.org/Documentation/wiki-page.view?name=gradleModules) for more information.

## Release Notes

### version ???
*Release*: ???
(Earliest compatible LabKey version: 19.3)

* Update reallyClean to depend on cleanSchemasCompile and thus remove the classes generated from xsd files
* Remove the schemas jar, incorporating the schema classes into the "main" (implementation) jar.
* Change publications to publish only the api jar and .module file
* Create task to publish module pom files to artifactory and harness transitive module dependencies

### veresion 1.7.0
*Release*: 27 Aug 2019
(Earliest compatible LabKey version: 19.1)

* Don't use yarn if there is a package-lock.json file.
* Add listNodeProjects task for showing which projects are using node in their build and which package manager
* Add links for both yarn and npm binaries
* [Issue 38198](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=38198): Find the nearest vcs root to use for populating vcs properties to accommodate modules nested in git repositories
* Modify incubating multi-git tasks to account for git modules not in optionalModules and testAutomation repo
* Add more incubating multi-git tasks: gitPull, gitPush, and gitStatus 
* Update reallyClean to depend on cleanNodeModules and thus also remove the node_modules directory for a module

### version 1.6.2
*Release*: 25 Jun 2019
(Earliest compatible LabKey version: 19.1)

* Fix for symlinkNode task (referencing unknown property)

### version 1.6.1
*Release*: 18 Jun 2019
(Earliest compatible LabKey version: 19.1)

* [Issue 37754](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=37754) - fix line endings for `manual-upgrade.sh`


### version 1.6
*Release*: 14 Jun 2019
(Earliest compatible LabKey version: 19.1)

* Update tasks for yarn support
* [Issue 36138](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=36138) - Remove compile-time dependency on local tomcat installation
  * Only require `tomcat.home`/`CATALINA_HOME` for tasks that use them

### version 1.5
*Release*: 23 May 2019
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
*Release*: 27 Mar 2019
(Earliest compatible LabKey version: 19.1)

* Build with Java 11 istead of 12

### version 1.4.3
*Release*: 25 Mar 2019
(Earliest compatible LabKey version: 19.1)

* Fix evaluation ordering for SpringConfig plugin when doing IntelliJ gradle syncing.

### version 1.4.2
*Release*: 20 Mar 2019
(Earliest compatible LabKey version: 19.1)

* Add ability to get base modules from a different location using the `platformProjectPath` property to
support some reorganization of the modules into git repositories.


### version 1.4.1
*Release*: 20 Feb 2019
(Earliest compatible LabKey version: 19.1)

* Remove Windows installer
* Update naming for alpha branches (formerly known as sprint branches)
* Convert to non-deprecated properties for artifact-producing tasks 
* (incubating) Add task for listing pull request info on a set of git repositories

### version 1.4
*Release*: 17 Jan 2019
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
*Release*: 14 Jan 2019
(Earliest compatible LabKey version: 18.3)

* [Issue 36527](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=36527) - Don't add branch name to artifact version if on release snapshot branch

### version 1.3.7
(skipped)

### version 1.3.6
*Release*: 28 Nov 2018
(Earliest compatible LabKey version: 18.3)

* Move ThreadDumpAndKill functionality into TeamCity plugin, where it is used
* Change schemaCompile task to target Java 1.8 instead of project sourceCompatibility property to avoid warnings
* [Issue 36034](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=36034) - eliminate copying of api jar into WEB-INF/lib
* [Issue 35902](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=35902) - make undeployModule work for file-based modules
* Add 'extraCatalinaOpts' property to pass additional startup options to Tomcat
* [Issue 36171](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=36171) - make the contents of the .tar.gz and .zip distributions identical

### version 1.3.5
*Release*: 29 Oct 2018
(Earliest compatible LabKey version: 18.3)

* When copying the labkey.xml file to the tomcat conf directory, don't throw an exception if the directory does not exist but the user can create it (via the copy)
* Add VcsBranch and VcsTag to module properties xml file
* Handle alpha branch names for naming distributions (releaseX.Y.Z-alpha.W)
* Properly translate artifact versions with a patch version (X.Y.Z) to LabKey module version (X.Y)
* Removed TestRunner dependency on sardine.

### version 1.3.4
*Release*: 29 Oct 2018
(Earliest compatible LabKey version: 18.2)

(Accidental no-change release)

### version 1.3.3
*Release*: 18 Oct 2018
(Earliest compatible LabKey version: 18.2)

* add ability to enable ldap sync configuration in labkey.xml with -PenableLdapSync.  This will uncomment a stanza in the labkey.xml
that is surrounded by &lt;`--@@ldapSyncConfig@@` and `@@ldapSyncConfig@@`--&gt;
* [Issue 35442](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=35442) - throw exception if tomcat directory is not present or not writeable when copying jars there
* add descriptions for our custom configurations (for use in task that shows all configurations)
* update to allow schemas project to be merged into the api project
* update pattern for jar checking to account for words in the release version (e.g., Spring's 4.3.0.RELEASE)

### version 1.3.2
*Release*: 29 Aug 2019
(Earliest compatible LabKey version: 18.2)

* [Issue 34390](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=34523) - make createModule use lower case name
for directory name (to correspond to package name)
* Clean up geckdriver processes on TeamCity (Selenium 3 support)
* Add new labkey configuration for use in declaring dependencies that do not need to be in the jars.txt file
* [Issue 35207](https://www.labkey.org/Rochester/support%20tickets/issues-details.view?issueId=35207) - make 
linking to npm executables work when not building from source
* Update template for createModule task to parameterize version number and copyright year

### version 1.3.1
*Released*: 19 June 2018
(Earliest compatible LabKey version: 18.2)

* Remove code that attempted (but failed) to create symbolic links to node and npm directories on Windows. 

### version 1.3
*Released*: 18 June 2018
(Earliest compatible LabKey version: 18.2)

* [Issue 34523](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=34523) - Change configuration for NPM plugin to download
specific versions of node and npm if appropriate properties are set
* Added cleanNodeModules task that will remove a project's node_modules directory
* Change JavaModule plugin to remove ```src``` as a resource directory by default.  Individual modules can declare it as a resource if needed.
* Parameterize gwt build so you can choose the target permutation browser in dev mode (using property gwtBrowser)
* [Issue 33473](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=33473) - always overwrite tomcat
lib jars to facilitate switching between newer and older versions of LabKey distributions
* [Issue 34388](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=34388) - clean out directories
created when compiling xsd's to jar file if a new jar is to be created.
* Update tasks that check version conflicts for jars and modules (no longer incubating). By default, the build will fail if version conflicts
are found.  See the documentation on [Version Conflicts in Local Builds](https://labkey.org/Documentation/wiki-page.view?name=gradleDepend) for more information. 
* [Issue 33858](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=33858) add checks for the 
existence of ```internal/gwtsrc``` so we can move it to its proper home in api.  
* Parameterize the location of some of the key, non-standard modules to make them easier to move around.  Parameter are ```apiProjectPath```,
```bootstrapProjectPath```, ```internalProjectPath```, ```remoteapiProjectPath```, ```schemasProjectPath```, ```coreProjectPath```.  These parameters are attached to the Gradle extension in the ```settings.gradle``` file (via the ```gradle/settings/parameters.gradle``` file).
* [Issue 33860](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=33860) - parameterization to 
allow for moving or removing :schemas project.  Parameter is ```schemasProjectPath``` attached to the Gradle extension in the 
```settings.gradle``` file.
* [Issue 30536](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=30536) - copy moduleTemplate into
gradle plugins repository and modify build of plugins jar to include a zip of the moduleTemplate (that will include
the empty directories that won't migrate to git).  Actual removal of moduleTemplate will not happen until LabKey 18.3.

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
destination directories.  See the issue for details on the tasks and the properties available to enable these tasks.

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

### version 1.2
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

### version 1.1

*Released*: 3 August 2017
(Earliest compatible LabKey version: 17.2)

* [Issue 31046](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31046) - Remove JSP jars from WEB-INF/jsp directory with undeployModule
* [Issue 31044](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=31044) - Exclude 
'out' directory generated by IntellijBuilds when finding input files for Antlr
* [Issue 30916](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=30916) - Prevent duplicate bootstrap 
jar files due to including branch names in version numbers

### version 1.0.1

*Released*: 2 July 2017
(Earliest compatible LabKey version: 17.2)

The first official release of the plugin to support Labkey 17.2 release.  

