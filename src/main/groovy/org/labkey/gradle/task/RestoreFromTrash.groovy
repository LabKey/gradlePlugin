package org.labkey.gradle.task

import org.apache.commons.lang3.StringUtils
import org.apache.hc.client5.http.classic.methods.HttpPost
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
import org.labkey.gradle.util.TaskUtils

import static org.labkey.gradle.task.PurgeArtifacts.Response

class RestoreFromTrash extends DefaultTask
{
    public static final String VERSION_PROPERTY = "restoreVersion"
    public static final String VERSIONS_FILE_PROPERTY = 'restoreVersions'
    public static final String RESTORE_LIST_FILE_PROPERTY = 'restoreList';

    @Input @Optional
    final abstract Property<String> restoreVersion = project.objects.property(String).convention(project.hasProperty(VERSION_PROPERTY) ? (String) project.property(VERSION_PROPERTY) : "")

    @Input @Optional
    final abstract Property<String> restoreVersions = project.objects.property(String).convention(project.hasProperty(VERSIONS_FILE_PROPERTY) ? (String) project.property(VERSIONS_FILE_PROPERTY) : "")
    @Input @Optional
    final abstract Property<String> restoreListFile = project.objects.property(String).convention(project.hasProperty(RESTORE_LIST_FILE_PROPERTY) ? (String) project.property(RESTORE_LIST_FILE_PROPERTY) : "")
    @Input
    final abstract Property<Boolean> isDryRun = project.objects.property(Boolean).convention(project.hasProperty(PurgeArtifacts.DRY_RUN_PROPERTY))

    @Input
    final abstract Property<String> artifactoryUrl = project.objects.property(String).convention((String) project.property('artifactory_contextUrl'))
    @Input
    final abstract Property<String> artifactoryUser = project.objects.property(String).convention((String) project.property('artifactory_user'))
    @Input
    final abstract Property<String> artifactoryPassword = project.objects.property(String).convention((String) project.property('artifactory_password'))

    private static final String NUM_NOT_FOUND = "numNotFound"
    private static final String NUM_RESTORED = "numRestored"
    private static final String UNRESTORED_VERSIONS = "unrestoredVersions"

    @TaskAction
    void restoreVersions()
    {
        String restoreModulesFileName = restoreListFile.get();
        if (StringUtils.isEmpty(restoreModulesFileName))
            throw new GradleException("Use -P${RESTORE_LIST_FILE_PROPERTY}=<moduleNames.txt> to provide a list of modules to work with.")
        List<String> moduleNames = TaskUtils.readInputFile(restoreModulesFileName, "modules", logger)
        if (moduleNames.isEmpty())
            throw new GradleException("No module names found in file ${restoreModulesFileName}")
        String version = this.restoreVersion.get()
        if (!StringUtils.isEmpty(version.trim()))
            this.restoreVersion(version, moduleNames)
        else
        {
            Map<String, Integer> overallStats = new HashMap<>()
            overallStats.put(NUM_NOT_FOUND, 0)
            overallStats.put(NUM_RESTORED, 0)
            String restoreVersionsFileName = restoreVersions.get()
            if (StringUtils.isEmpty(restoreVersionsFileName))
                throw new GradleException("Either -P${VERSION_PROPERTY}=<versionToPurge> or -P${VERSIONS_FILE_PROPERTY}=<versionsFile.txt> must be provided")
            List<String> versions = TaskUtils.readInputFile(restoreVersionsFileName, "versions", logger)
            if (versions.isEmpty())
                throw new GradleException("No versions found for file ${restoreVersionsFileName}.")
            if (versions.size() > 1) {
                for (String moduleName : moduleNames) {
                    Map<String, Object> restoreStats = restoreModuleVersions(moduleName, versions)
                    overallStats.put(NUM_NOT_FOUND, overallStats.get(NUM_NOT_FOUND) + (Integer) restoreStats.get(NUM_NOT_FOUND))
                    overallStats.put(NUM_RESTORED, overallStats.get(NUM_RESTORED) + (Integer) restoreStats.get(NUM_RESTORED))
                }
                if (moduleNames.size() > 1)
                    logger.quiet("\nSummary:\n\tRestored ${overallStats.get(NUM_RESTORED)} artifacts.\n\t${overallStats.get(NUM_NOT_FOUND)} artifacts not found.")
            }
            else {
                for (String v : versions) {
                    if (!StringUtils.isEmpty(v.trim()))
                    this.restoreVersion(v, moduleNames)
                }
            }
        }
    }

    Map<String, Object> restoreModuleVersions(String moduleName, List<String> versions)
    {
        Map<String, Object> restoreStats = new HashMap();
        restoreStats.put(NUM_RESTORED, 0)
        restoreStats.put(NUM_NOT_FOUND, 0)
        restoreStats.put(UNRESTORED_VERSIONS, new ArrayList<>())

        logger.quiet("### Begin restore for module ${moduleName} for ${versions.size()} versions\n")

        for (String version: versions) {
            if (!StringUtils.isEmpty(version.trim()))
                makeRequests(moduleName, moduleName, version, restoreStats)
        }
        logStats(restoreStats)
        logger.quiet("\n### End restore for module ${moduleName}\n")
        return restoreStats
    }

    Map<String, Object> restoreVersion(String version, List<String> moduleNames)
    {
        if (StringUtils.isEmpty(version.trim()))
            return
        Map<String, Object> restoreStats = new HashMap();
        restoreStats.put(NUM_RESTORED, 0)
        restoreStats.put(NUM_NOT_FOUND, 0)
        restoreStats.put(UNRESTORED_VERSIONS, new ArrayList<>())
        for (String moduleName: moduleNames) {
            makeRequests(moduleName, moduleName, version, restoreStats)
        }
        logStats(restoreStats)
        return restoreStats
    }

    void logStats(Map<String, Object> restoreStats)
    {
        logger.quiet("Restored ${restoreStats.get(NUM_RESTORED)} artifacts; ${restoreStats.get(NUM_NOT_FOUND)} artifacts not found.")
        if (((List<String>) restoreStats.get(UNRESTORED_VERSIONS)).size() > 0 && !isDryRun.get())
            throw new GradleException("The following ${((List<String>) restoreStats.get(UNRESTORED_VERSIONS)).size()} versions were not restored.\n${StringUtils.join(restoreStats.get(UNRESTORED_VERSIONS), "\n")}\nCheck the log for more information.")
    }

    void makeRequests(String moduleName, String loggingName, String restoreVersion, Map<String, Object> statsMap)
    {
        if (StringUtils.isEmpty(restoreVersion.trim()))
            return
        logger.quiet("Considering ${loggingName} ${restoreVersion}...")
        Response response = makeRestoreRequest(moduleName, restoreVersion, "module")
        if (response == Response.NOT_FOUND)
            statsMap.put(NUM_NOT_FOUND, statsMap.get(NUM_NOT_FOUND)+1);
        else if (response == Response.ERROR)
            statsMap.get(UNRESTORED_VERSIONS).add("${loggingName} - module: ${restoreVersion}")
        else
            statsMap.put(NUM_RESTORED, statsMap.get(NUM_RESTORED)+1)

        response = makeRestoreRequest(moduleName, restoreVersion, "api")
        if (response == Response.NOT_FOUND)
            statsMap.put(NUM_NOT_FOUND, statsMap.get(NUM_NOT_FOUND)+1)
        else if (response == Response.ERROR)
            statsMap.get(UNRESTORED_VERSIONS).add("${loggingName} - api: ${restoreVersion}")
        else
            statsMap.put(NUM_RESTORED, statsMap.get(NUM_RESTORED)+1)
    }

    /**
     * This uses the Artifactory REST Api to request a restoration of a particular "item" (https://jfrog.com/help/r/jfrog-rest-apis/restore-item-from-trash-can)
     *
     * @param artifactName the artifact whose version is to be restored
     * @param version the version of the artifact to restore (e.g., 21.11-SNAPSHOT)
     * @param type either "api" or "module"
     * @return Response summarizing http status from request
     * @throws GradleException if the restoration request throws an exception
     */
    Response makeRestoreRequest(String artifactName, String version, String type)
    {
        if (project.hasProperty("dryRun")) {
            logger.quiet("\tRestoring version ${version} of ${artifactName} ${type} -- Skipped for dry run")
            return null
        }

        CloseableHttpClient httpClient = HttpClients.createDefault()
        String endpoint = project.property('artifactory_contextUrl')
        Response responseStatus = Response.SUCCESS
        if (!endpoint.endsWith("/"))
            endpoint += "/"
        endpoint += "api/trash/restore/"

        String repo = version.contains("SNAPSHOT") ? PurgeArtifacts.SNAPSHOT_REPOSITORY_NAME : PurgeArtifacts.RELEASE_REPOSITORY_NAME
        String path = "${repo}/org/labkey/${type}/${artifactName}/${version}"
        endpoint += "${path}?to=${path}"
        logger.quiet("\tMaking restore request for ${type} artifact ${artifactName} and version ${version} via endpoint ${endpoint}")

        try
        {
            HttpPost httpPost = new HttpPost(endpoint)
            // N.B. Using Authorization Bearer with an API token does not currently work
            httpPost.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString("${project.property('artifactory_user')}:${project.property('artifactory_password')}".getBytes()))
            CloseableHttpResponse response = httpClient.execute(httpPost)
            int statusCode = response.getCode()

            if (statusCode == HttpStatus.SC_NOT_FOUND) {
                logger.info("No such file or directory: ${endpoint}")
                responseStatus = Response.NOT_FOUND
            }
            else if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_NO_CONTENT && statusCode != HttpStatus.SC_ACCEPTED) {
                logger.error("Unable to restore using ${endpoint}: ${statusCode} ${response.getReasonPhrase()}")
                responseStatus = Response.ERROR
            }
            response.close()
            return responseStatus
        }
        catch (Exception e)
        {
            throw new GradleException("Problem executing restore request with url ${endpoint}", e)
        }
        finally
        {
            httpClient.close()
        }
    }
}
