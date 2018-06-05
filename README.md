## gradlePlugin

The gradlePlugin jar is a jar file containing plugins, tasks, extensions and utilities used for building the [LabKey](https://www.labkey.org)
Server application and its modules.

If building your own LabKey module, you may choose to use these plugins or not.  They bring in a lot of functionality 
but also make certain assumptions that you may not want to impose on your module.  See the 
[LabKey documentation](https://www.labkey.org/Documentation/wiki-page.view?name=gradleModules) for more information.

## Release Notes

### version 1.2.8
*Released*: 5 June 2018
(Earliest compatible LabKey version: 18.2)

* added TeamCity parameter testValidationOnly for test that will do validation only (e.g. upgrade tests, blue-green)
* dropDatabase will not happen if testValidationOnly is true


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
* [Issue 34078](https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=34078) - update destintion
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

