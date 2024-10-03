package org.labkey.gradle.task

import org.apache.commons.lang3.StringUtils
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.HttpStatus
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.Api
import org.labkey.gradle.plugin.FileModule
import org.labkey.gradle.plugin.JavaModule
import org.labkey.gradle.plugin.Module
import org.labkey.gradle.util.BuildUtils

import static org.labkey.gradle.task.PurgeArtifacts.Response

class RestoreFromTrash extends DefaultTask
{
    public static final String VERSION_PROPERTY = "restoreVersion"

    @TaskAction
    void restoreVersions()
    {
        String restoreVersion
        if (!project.hasProperty(VERSION_PROPERTY))
            throw new GradleException("No value provided for ${VERSION_PROPERTY}.")
        restoreVersion = project.property(VERSION_PROPERTY)
        String[] unrestoredVersions = []
        int numRestored = 0
        int numNotFound = 0
        project.allprojects({ Project p ->
            def plugins = p.getPlugins()
            if (plugins.hasPlugin(Module.class) || plugins.hasPlugin(JavaModule.class) || plugins.hasPlugin(FileModule.class)) {
                logger.quiet("Considering ${p.path}...")
                Response response = makeRestoreRequest(p.name, restoreVersion, "module")
                if (response == Response.NOT_FOUND)
                    numNotFound++
                else if (response == Response.ERROR) {
                    unrestoredVersions += "${p.path} - module: ${restoreVersion}"
                } else
                    numRestored++
            }
            if (plugins.hasPlugin(Api.class) || project.path == BuildUtils.getApiProjectPath(project.gradle)) {
                Response response = makeRestoreRequest(p.name, restoreVersion, "api")
                if (response == Response.NOT_FOUND)
                    numNotFound++
                else if (response == Response.ERROR) {
                    unrestoredVersions += "${p.path} - api: ${restoreVersion}"
                } else
                    numRestored++
            }
        })

        logger.quiet("Restored ${numRestored} artifacts; ${numNotFound} artifacts not found.")
        if (unrestoredVersions.size() > 0 && !project.hasProperty("dryRun"))
            throw new GradleException("The following ${unrestoredVersions.size()} versions were not restored.\n${StringUtils.join(unrestoredVersions, "\n")}\nCheck the log for more information.")

    }

    /**
     * This uses the Artifactory REST Api to request a restoration of a particular "item" (https://jfrog.com/help/r/jfrog-rest-apis/restore-item-from-trash-can)
     *
     * @param artifactName the artifact whose version is to be deleted
     * @param version the version of the artifact to delete (e.g., 21.11-SNAPSHOT)
     * @param type either "api" or "module"
     * @return Response summarizing http status from request
     * @throws GradleException if the restoration request throws an exception
     */
    Response makeRestoreRequest(String artifactName, String version, String type)
    {
        if (project.hasProperty("dryRun")) {
            logger.quiet("\tRestoring version ${version} of ${artifactName} ${type} -- Skipped for dry run")
            return
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
