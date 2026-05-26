/*
 * Copyright (c) 2023-2026 LabKey Corporation
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


import org.apache.commons.lang3.StringUtils
import org.apache.hc.client5.http.classic.methods.HttpDelete
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.HttpStatus
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.work.DisableCachingByDefault
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.TaskUtils

@UntrackedTask(because="External side effects only")
class PurgeArtifacts extends DefaultTask
{
    public static final String SNAPSHOT_REPOSITORY_NAME = 'libs-snapshot-local'
    public static final String RELEASE_REPOSITORY_NAME = 'libs-release-local'
    public static final String VERSION_PROPERTY = 'purgeVersion'
    public static final String VERSIONS_FILE_PROPERTY = 'purgeVersions'
    public static final String PURGE_LIST_FILE_PROPERTY = 'purgeList'
    public static final String DRY_RUN_PROPERTY = 'dryRun';

    @Input @Optional
    final abstract Property<String> purgeVersion = project.objects.property(String).convention(project.hasProperty(VERSION_PROPERTY) ? (String) project.property(VERSION_PROPERTY) : "")
    @Input @Optional
    final abstract Property<String> purgeVersions = project.objects.property(String).convention(project.hasProperty(VERSIONS_FILE_PROPERTY) ? (String) project.property(VERSIONS_FILE_PROPERTY) : "")
    @Input @Optional
    final abstract Property<String> purgeListFile = project.objects.property(String).convention(project.hasProperty(PURGE_LIST_FILE_PROPERTY) ? (String) project.property(PURGE_LIST_FILE_PROPERTY) : "")
    @Input
    final abstract Property<Boolean> isDryRun = project.objects.property(Boolean).convention(project.hasProperty(DRY_RUN_PROPERTY))

    @Input
    final abstract Property<String> artifactoryUrl = project.objects.property(String).convention((String) project.property(BuildUtils.ARTIFACTORY_CONTEXT_URL_PROP))
    @Input
    final abstract Property<String> artifactoryUser = project.objects.property(String).convention((String) project.property(BuildUtils.ARTIFACTORY_USER_PROP))
    @Input
    final abstract Property<String> artifactoryPassword = project.objects.property(String).convention((String) project.property(BuildUtils.ARTIFACTORY_PASSWORD_PROP))

    enum Response {
        SUCCESS,
        NOT_FOUND,
        ERROR
    }
    private static final String NUM_NOT_FOUND = "numNotFound"
    private static final String NUM_DELETED = "numDeleted"
    private static final String UNDELETED_VERSIONS = "undeletedVersions"

    @TaskAction
    void purgeVersions()
    {
        String version = purgeVersion.get()
        String purgeModulesFileName = purgeListFile.get()
        if (StringUtils.isEmpty(purgeModulesFileName))
            throw new GradleException("Use -P${PURGE_LIST_FILE_PROPERTY}=<moduleNames.txt> to provide a list of modules to work with.")
        List<String> moduleNames = TaskUtils.readInputFile(purgeListFile.get(), "modules", logger)
        if (moduleNames.isEmpty())
            throw new GradleException("No module names found in file ${purgeListFile.get()}")
        if (!StringUtils.isEmpty(version))
            purgeVersion(version, moduleNames)
        else
        {
            Map<String, Integer> overallStats = new HashMap<>()
            List<String> inactiveModules = new ArrayList<>()
            overallStats.put(NUM_NOT_FOUND, 0)
            overallStats.put(NUM_DELETED, 0)
            String purgeVersionsFileName = purgeVersions.get()
            if (StringUtils.isEmpty(purgeVersionsFileName))
                throw new GradleException("Either -P${VERSION_PROPERTY}=<versionToPurge> or -P${VERSIONS_FILE_PROPERTY}=<versionsFile.txt> must be provided")
            List<String> versions = TaskUtils.readInputFile(purgeVersionsFileName, "versions", logger)
            if (versions.isEmpty())
                throw new GradleException("No versions found for file ${purgeVersionsFileName}.")
            if (versions.size() > 1) {
                for (String moduleName : moduleNames) {
                    Map<String, Object> deleteStats = purgeModuleVersions(moduleName, versions)
                    if (deleteStats.get(NUM_DELETED) == 0) // if none of the versions in question were deleted, we may have removed all versions of this module and can remove it from consideration
                        inactiveModules.add(moduleName)
                    overallStats.put(NUM_NOT_FOUND, overallStats.get(NUM_NOT_FOUND) + (Integer) deleteStats.get(NUM_NOT_FOUND))
                    overallStats.put(NUM_DELETED, overallStats.get(NUM_DELETED) + (Integer) deleteStats.get(NUM_DELETED))
                }
                if (moduleNames.size() > 1) {
                    logger.quiet("\nSummary:\n\tDeleted ${overallStats.get(NUM_DELETED)} artifacts.\n\t${overallStats.get(NUM_NOT_FOUND)} artifacts not found.")
                    if (!inactiveModules.isEmpty())
                        logger.quiet("\n\tModules with no artifacts deleted: " + inactiveModules.join(", "))
                }
            }
            else {
                for (String v : versions) {
                    Map<String, Object> deleteStats = purgeVersion(v, moduleNames)
                    overallStats.put(NUM_NOT_FOUND, overallStats.get(NUM_NOT_FOUND) + (Integer) deleteStats.get(NUM_NOT_FOUND))
                    overallStats.put(NUM_DELETED, overallStats.get(NUM_DELETED) + (Integer) deleteStats.get(NUM_DELETED))
                }
                if (versions.size() > 1)
                    logger.quiet("\nSummary\n\tDeleted ${overallStats.get(NUM_DELETED)} artifacts.\n\t${overallStats.get(NUM_NOT_FOUND)} artifacts not found.")
            }
        }

    }


    Map<String, Object> purgeModuleVersions(String moduleName, List<String> versions)
    {
        Map<String, Object> deleteStats = new HashMap();
        deleteStats.put(NUM_DELETED, 0)
        deleteStats.put(NUM_NOT_FOUND, 0)
        deleteStats.put(UNDELETED_VERSIONS, new ArrayList<>())

        logger.quiet("### Begin purge for module ${moduleName} for ${versions.size()} versions\n")

        for (String version: versions) {
            if (!StringUtils.isEmpty(version.trim()))
                makeRequests(moduleName, moduleName, version, deleteStats, true)
        }

        logger.quiet("Deleted ${deleteStats.get(NUM_DELETED)} artifacts; ${deleteStats.get(NUM_NOT_FOUND)} artifacts not found.")
        if (((List<String>) deleteStats.get(UNDELETED_VERSIONS)).size() > 0 && !isDryRun.get())
            throw new GradleException("The following ${((List<String>) deleteStats.get(UNDELETED_VERSIONS)).size()} versions were not deleted.\n${StringUtils.join(deleteStats.get(UNDELETED_VERSIONS), "\n")}\nCheck the log for more information.")
        logger.quiet("\n### End purge for module ${moduleName}\n")
        return deleteStats
    }

    Map<String, Object> purgeVersion(String version, List<String> moduleNames)
    {
        Map<String, Object> deleteStats = new HashMap();
        deleteStats.put(NUM_DELETED, 0)
        deleteStats.put(NUM_NOT_FOUND, 0)
        deleteStats.put(UNDELETED_VERSIONS, new ArrayList<>())

        logger.quiet("### Begin purge of version ${version} for ${moduleNames.size()} modules\n")

        for (String moduleName : moduleNames) {
            makeRequests(moduleName, moduleName, version, deleteStats, true)
        }

        logger.quiet("Deleted ${deleteStats.get(NUM_DELETED)} artifacts; ${deleteStats.get(NUM_NOT_FOUND)} artifacts not found.")
        if (((List<String>) deleteStats.get(UNDELETED_VERSIONS)).size() > 0 && !isDryRun.get())
            throw new GradleException("The following ${((List<String>) deleteStats.get(UNDELETED_VERSIONS)).size()} versions were not deleted.\n${StringUtils.join(deleteStats.get(UNDELETED_VERSIONS), "\n")}\nCheck the log for more information.")
        logger.quiet("\n### End purge for version ${version}\n")
        return deleteStats
    }

    void makeRequests(String moduleName, String loggingName, String purgeVersion, Map<String, Object> statsMap, boolean tryApi)
    {
        logger.quiet("Considering ${loggingName} ${purgeVersion}...")
        Response response = makeDeleteRequest(moduleName, purgeVersion, "module")
        if (response == Response.NOT_FOUND)
            statsMap.put(NUM_NOT_FOUND, statsMap.get(NUM_NOT_FOUND)+1);
        else if (response == Response.ERROR)
            statsMap.get(UNDELETED_VERSIONS).add("${loggingName} - module: ${purgeVersion}")
        else
            statsMap.put(NUM_DELETED, statsMap.get(NUM_DELETED)+1)
        if (tryApi) {
            response = makeDeleteRequest(moduleName, purgeVersion, "api")
            if (response == Response.NOT_FOUND)
                statsMap.put(NUM_NOT_FOUND, statsMap.get(NUM_NOT_FOUND)+1)
            else if (response == Response.ERROR)
                statsMap.get(UNDELETED_VERSIONS).add("${loggingName} - api: ${purgeVersion}")
            else
                statsMap.put(NUM_DELETED, statsMap.get(NUM_DELETED)+1)
        }
    }

    /**
     * This uses the Artifactory REST Api to request a deletion of a particular "item" (https://jfrog.com/help/r/jfrog-rest-apis/delete-item)
     *
     * @param artifactName the artifact whose version is to be deleted
     * @param version the version of the artifact to delete (e.g., 21.11-SNAPSHOT)
     * @param type either "api" or "module"
     * @return true if deletion was successful, false otherwise
     * @throws GradleException if the delete request throws an exception
     */
    Response makeDeleteRequest(String artifactName, String version, String type)
    {
        if (isDryRun.get()) {
            logger.quiet("\tRemoving version ${version} of ${artifactName} ${type} -- Skipped for dry run")
            return
        }

        CloseableHttpClient httpClient = HttpClients.createDefault()
        String endpoint = artifactoryUrl.get()
        Response responseStatus = Response.SUCCESS
        if (!endpoint.endsWith("/"))
            endpoint += "/"

        String repo = version.contains("SNAPSHOT") ? SNAPSHOT_REPOSITORY_NAME : RELEASE_REPOSITORY_NAME
        endpoint += repo + "/org/labkey/" + type + "/" + artifactName + "/" + version
        logger.quiet("\tMaking delete request for ${type} artifact ${artifactName} and version ${version} via endpoint ${endpoint}")

        try
        {
            HttpDelete httpDelete = new HttpDelete(endpoint)
            // N.B. Using Authorization Bearer with an API token does not currently work
            httpDelete.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString("${artifactoryUser.get()}:${artifactoryPassword.get()}".getBytes()))
            CloseableHttpResponse response = httpClient.execute(httpDelete)
            int statusCode = response.getCode()

            if (statusCode == HttpStatus.SC_NOT_FOUND) {
                logger.info("No such file or directory: ${endpoint}")
                responseStatus = Response.NOT_FOUND
            }
            else if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_NO_CONTENT) {
                logger.error("Unable to delete using ${endpoint}: ${statusCode} ${response.getReasonPhrase()}")
                responseStatus = Response.ERROR
            }
            response.close()
            return responseStatus
        }
        catch (Exception e)
        {
            throw new GradleException("Problem executing delete request with url ${endpoint}", e)
        }
        finally
        {
            httpClient.close()
        }
    }
}
