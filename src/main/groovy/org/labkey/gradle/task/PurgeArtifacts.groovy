package org.labkey.gradle.task

import org.apache.commons.lang3.StringUtils
import org.apache.http.HttpStatus
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpDelete
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.Api
import org.labkey.gradle.plugin.Module
import org.labkey.gradle.plugin.FileModule
import org.labkey.gradle.plugin.JavaModule
import org.labkey.gradle.util.BuildUtils

class PurgeArtifacts extends DefaultTask
{
    private static final String SNAPSHOT_REPOSITORY_NAME = 'libs-snapshot-local'
    private static final String RELEASE_REPOSITORY_NAME = 'libs-release-local'
    public static final String VERSION_PROPERTY = 'purgeVersion'

    static boolean isPreSplitVersion(String version)
    {
        return BuildUtils.compareVersions( version, "19.3") < 0
    }

    enum Response {
        SUCCESS,
        NOT_FOUND,
        ERROR
    }

    @TaskAction
    void purgeVersions()
    {
        String purgeVersion;
        if (!project.hasProperty(VERSION_PROPERTY))
            throw new GradleException("No value provided for ${VERSION_PROPERTY}.")
        purgeVersion = project.property(VERSION_PROPERTY)
        boolean isPreSplitVersion = isPreSplitVersion(purgeVersion)
        String[] undeletedVersions = []
        int numDeleted = 0
        int numNotFound = 0
        project.allprojects({ Project p ->
            def plugins = p.getPlugins()
            if (plugins.hasPlugin(Module.class) || plugins.hasPlugin(JavaModule.class) || plugins.hasPlugin(FileModule.class)) {
                logger.quiet("Considering ${p.path}...")
                Response response = makeDeleteRequest(p.name, purgeVersion, "module")
                if (response == Response.NOT_FOUND)
                    numNotFound++
                else if (response == Response.ERROR) {
                    undeletedVersions += "${p.path} - module: ${purgeVersion}"
                }
                else
                    numDeleted++
                if (!isPreSplitVersion && plugins.hasPlugin(Api.class)) {
                    response = makeDeleteRequest(p.name, purgeVersion, "api")
                    if (response == Response.NOT_FOUND)
                        numNotFound++
                    else if (response == Response.ERROR) {
                        undeletedVersions += "${p.path} - api: ${purgeVersion}"
                    }
                    else
                        numDeleted++
                }
            }
        })

        logger.quiet("Deleted ${numDeleted} artifacts; ${numNotFound} artifacts not found.")
        if (undeletedVersions.size() > 0 && !project.hasProperty("dryRun"))
            throw new GradleException("The following ${undeletedVersions.size()} versions were not deleted.\n${StringUtils.join(undeletedVersions, "\n")}\nCheck the log for more information.")
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
        if (project.hasProperty("dryRun")) {
            logger.quiet("\tRemoving version ${version} of ${artifactName} ${type} -- Skipped for dry run")
            return
        }

        CloseableHttpClient httpClient = HttpClients.createDefault();
        String endpoint = project.property('artifactory_contextUrl')
        Response responseStatus = Response.SUCCESS
        if (!endpoint.endsWith("/"))
            endpoint += "/"

        String repo = version.contains("SNAPSHOT") ? SNAPSHOT_REPOSITORY_NAME : RELEASE_REPOSITORY_NAME
        if (isPreSplitVersion(version))
            endpoint += repo + "/org/labkey/" + artifactName + "/" + version
        else
            endpoint += repo + "/org/labkey/" + type + "/" + artifactName + "/" + version
        logger.quiet("\tMaking delete request for ${type} artifact ${artifactName} and version ${version} via endpoint ${endpoint}")

        try
        {
            HttpDelete httpDelete = new HttpDelete(endpoint)
            // N.B. Using Authorization Bearer with an API token does not currently work
            httpDelete.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString("${project.property('artifactory_user')}:${project.property('artifactory_password')}".getBytes()))
            CloseableHttpResponse response = httpClient.execute(httpDelete)
            int statusCode = response.getStatusLine().getStatusCode()

            if (statusCode == HttpStatus.SC_NOT_FOUND) {
                logger.info("No such file or directory: ${endpoint}")
                responseStatus = Response.NOT_FOUND
            }
            else if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_NO_CONTENT) {
                logger.error("Unable to delete using ${endpoint}: ${response.getStatusLine()}")
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
