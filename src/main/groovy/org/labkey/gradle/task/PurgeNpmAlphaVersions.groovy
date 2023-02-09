package org.labkey.gradle.task

import groovy.json.JsonSlurper
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.http.HttpStatus
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpDelete
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.NpmRun

import java.util.stream.Collectors

class PurgeNpmAlphaVersions extends DefaultTask
{
    private static final String REPOSITORY_NAME = 'libs-client-local'
    public static final String ALPHA_PREFIX_PROPERTY = 'alphaPrefix'
    public static final String[] PACKAGE_NAMES = [
            '@labkey/api',
            '@labkey/assayreport',
            '@labkey/build',
            '@labkey/components',
            '@labkey/premium',
            '@labkey/test',
            '@labkey/themes'
    ]

    @TaskAction
    void purgeVersions()
    {
        String alphaPrefix;
        if (!project.hasProperty(ALPHA_PREFIX_PROPERTY))
            throw new GradleException("No value provided for alphaPrefix.")
        alphaPrefix = project.property(ALPHA_PREFIX_PROPERTY)
        String[] undeletedVersions = []
        for (String packageName : PACKAGE_NAMES)
        {
            project.logger.quiet("Considering ${packageName}...")
            List<String> alphaVersions = getNpmAlphaVersions(packageName, alphaPrefix)
            project.logger.quiet("Found ${alphaVersions.size()} versions with alpha prefix ${alphaPrefix} in package ${packageName}")
            if (!alphaVersions.isEmpty())
            {
                alphaVersions.forEach(version -> {
                    if (project.hasProperty("dryRun"))
                        project.logger.quiet("Removing version ${version} of package ${packageName} -- Skipped for dry run")
                    else {
                        project.logger.quiet("Removing version ${version} of package ${packageName}")
                        if (!makeDeleteRequest(packageName, version)) {
                            undeletedVersions += "${packageName}: ${version}"
                        }
                    }
                })
            }
        }
        if (undeletedVersions.size() > 0)
            throw new GradleException("The following versions were not deleted.\n${undeletedVersions}\nCheck the log for more information.")
    }


    private static List<String> getNpmAlphaVersions(String packageName, String alphaPrefix)
    {
        String output = (NpmRun.getNpmCommand() + " view ${packageName} versions --json").execute().text
        if (!StringUtils.isEmpty(output)) {
            def parsedJson = new JsonSlurper().parseText(output)
            if (parsedJson instanceof ArrayList) {
                return parsedJson.stream().filter(version -> {
                    version.matches(".+-" + alphaPrefix + "\\.\\d+")
                }).collect(Collectors.toList())
            } else
                throw new GradleException("Error retrieving versions for package ${packageName}: ${parsedJson.error}")
        }
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
     * @param version the version of the pacakge to delete (e.g., 2.14.2-fb-update-react-select.1)
     * @return true if deletion was successful, false otherwise
     * @throws GradleException if the delete request throws an exception
     */
    boolean makeDeleteRequest(String packageName, String version)
    {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String endpoint = project.property('artifactory_contextUrl')
        boolean success = true
        if (!endpoint.endsWith("/"))
            endpoint += "/"

        // The coordinates of the packages look like this: "@labkey/components/-/@labkey/components-2.14.2-fb-update-react-select.1.tgz"
        endpoint += REPOSITORY_NAME + "/" + packageName + "/-/" + packageName + "-" + version + ".tgz"
        project.logger.debug("Making delete request for package ${packageName} and version ${version} via endpoint ${endpoint}")
        try
        {
            HttpDelete httpDelete = new HttpDelete(endpoint)
            // N.B. Using Authorization Bearer with an API token does not currently work
            httpDelete.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString("${project.property('artifactory_user')}:${project.property('artifactory_password')}".getBytes()))
            CloseableHttpResponse response = httpClient.execute(httpDelete)
            int statusCode = response.getStatusLine().getStatusCode()
            if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_NO_CONTENT) {
                project.logger.error("Unable to delete using ${endpoint}: ${response.getStatusLine()}")
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
