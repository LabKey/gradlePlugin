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
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.util.TaskUtils

abstract class PurgeNpmVersions extends DefaultTask
{
    private static final String REPOSITORY_NAME = 'libs-client-local'
    public static final String DRY_RUN_PROPERTY = 'dryRun'
    private static final String PACKAGE_NAME_PROP = "packageName"
    private static final String VERSION_LIST_PROP = "versionList"

    @Input
    final abstract Property<String> packageName = project.objects.property(String).convention((project.hasProperty(PACKAGE_NAME_PROP) ? (String) project.property(PACKAGE_NAME_PROP) : null))

    @Input
    final abstract Property<String> versionList = project.objects.property(String).convention((project.hasProperty(VERSION_LIST_PROP) ? (String) project.property(VERSION_LIST_PROP) : null))

    @Input
    final abstract Property<Boolean> isDryRun = project.objects.property(Boolean).convention(project.hasProperty(DRY_RUN_PROPERTY))
    @Input
    final abstract Property<String> artifactoryUrl = project.objects.property(String).convention((String) project.property('artifactory_contextUrl'))
    @Input
    final abstract Property<String> artifactoryUser = project.objects.property(String).convention((String) project.property('artifactory_user'))
    @Input
    final abstract Property<String> artifactoryPassword = project.objects.property(String).convention((String) project.property('artifactory_password'))

    @TaskAction
    void purgeVersions()
    {
        if (!packageName.isPresent() || StringUtils.isEmpty(packageName.get().trim()))
            throw new GradleException("No value provided for packageName.")
        String packageName = "@labkey/" + packageName.get()
        String[] undeletedVersions = []

        logger.quiet("Considering ${packageName}...")
        List<String> versions = readPurgeVersions()
        if (versions.isEmpty())
            logger.quiet("No versions provided.")
        else {
            logger.quiet("Found ${versions.size()} versions in package ${packageName}")
            versions.forEach(version -> {
                if (isDryRun.get())
                    logger.quiet("Removing version ${version} of package ${packageName} -- Skipped for dry run")
                else {
                    logger.quiet("Removing version ${version} of package ${packageName}")
                    if (!makeDeleteRequest(packageName, version)) {
                        undeletedVersions += "${packageName}: ${version}"
                    }
                }
            })
        }

        if (undeletedVersions.size() > 0)
            throw new GradleException("The following versions were not deleted.\n${undeletedVersions}\nCheck the log for more information.")
    }

    List<String> readPurgeVersions()
    {
        if (versionList.isPresent() && !StringUtils.isEmpty(versionList.get().trim()))
            return TaskUtils.readInputFile(versionList.get(), "versions", logger)
        else
            throw new GradleException("No " + VERSION_LIST_PROP + " or " + VERSION_LIST_PROP + " property provided.");
    }


    /**
     * This uses the Artifactory REST Api to request a deletion of a particular package and version.  There does
     * not appear to be a way to request deletion of multiple versions at once.  Also, though it might seem natural
     * to use "npm unpublish" for this deletion, this does not work with artifactory, possibly due to this long-standing
     * issue: https://github.com/npm/npm-registry-client/issues/41
     * The command appears to work, returning a 200 status code when you use --verbose logging, but the artifact doesn't
     * go anywhere.
     *
     * Another possibility here would be to use the same action as is used in the Web UI.  There, Artifactory sends
     * a POST request to:
     *     Request URL: https://artifactory.labkey.com/artifactory/ui/artifactactions/delete
     * with parameters
     *     repoKey: libs-client-local
     *     path: "@labkey/components/-/@labkey/components-2.14.2-fb-update-react-select.1.tgz"
     * The REST API seems a better approach, though.
     * @param packageName the package whose version is to be deleted, including the scope (e.g., @labkey/components)
     * @param version the version of the package to delete (e.g., 2.14.2-fb-update-react-select.1)
     * @return true if deletion was successful, false otherwise
     * @throws GradleException if the delete request throws an exception
     */
    protected boolean makeDeleteRequest(String packageName, String version)
    {
        CloseableHttpClient httpClient = HttpClients.createDefault()
        String endpoint = artifactoryUrl.get()
        boolean success = true
        if (!endpoint.endsWith("/"))
            endpoint += "/"

        // The coordinates of the packages look like this: "@labkey/components/-/@labkey/components-2.14.2-fb-update-react-select.1.tgz"
        endpoint += REPOSITORY_NAME + "/" + packageName + "/-/" + packageName + "-" + version + ".tgz"
        logger.debug("Making delete request for package ${packageName} and version ${version} via endpoint ${endpoint}")
        try
        {
            HttpDelete httpDelete = new HttpDelete(endpoint)
            // N.B. Using Authorization Bearer with an API token does not currently work
            httpDelete.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString("${artifactoryUser.get()}:${artifactoryPassword.get()}".getBytes()))
            CloseableHttpResponse response = httpClient.execute(httpDelete)
            int statusCode = response.getCode()
            if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_NO_CONTENT) {
                logger.error("Unable to delete using ${endpoint}: ${statusCode} ${response.getReasonPhrase()}")
                success = false
            }
            response.close()
            return success
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
