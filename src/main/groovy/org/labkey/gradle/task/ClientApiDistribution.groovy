/*
 * Copyright (c) 2017 LabKey Corporation
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

import org.gradle.api.DefaultTask
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.util.BuildUtils

class ClientApiDistribution extends DefaultTask
{
    public static final String CLIENT_API_JSDOC = "ClientAPI-JavaScript-Docs"
    public static final String JSDOC_CLASSIFIER = "jsdoc"
    public static final String XML_SCHEMA_DOC = "ClientAPI-XMLSchema-Docs"
    public static final String SCHEMA_DOC_CLASSIFIER = "schema-doc"

    File javaDir
    File javaJdbcDir
    File javascriptDir
    File xmlDir

    String apiDocsBuildDir
    String xsdDocsBuildDir

    ClientApiDistribution()
    {
        javaDir = new File(project.dist.dir, "/client-api/java")
        javaJdbcDir = new File(project.dist.dir, "/client-api/jdbc")
        javascriptDir = new File(project.dist.dir, "/client-api/javascript")
        xmlDir = new File(project.dist.dir, "/client-api/XML")

        apiDocsBuildDir = project.project(":server").tasks.jsdoc.outputs.getFiles().asPath
        xsdDocsBuildDir = project.project(":server").tasks.xsddoc.outputs.getFiles().asPath
        project.tasks.clean.doLast({
            project.delete project.fileTree(project.dist.dir) { include '**/TeamCity*.zip' }
        })
    }

    @TaskAction
    void doAction()
    {
        // we do this already in the remoteapi/java project
        createJavaDocs()

        createClientApiDocs()

        createXsdDocs()

        createTeamCityArchives()

        if(project.findProject(":remoteapi:labkey-api-jdbc") != null)
            createJdbcApi()
    }

    @OutputFile
    File getJavaClientApiFile()
    {
        return new File("${javaDir}/LabKey${BuildUtils.getDistributionVersion(project)}-ClientAPI-Java.zip")
    }

    @OutputFile
    File getJavaClientApiSrcFile()
    {
        return new File("${javaDir}/LabKey${BuildUtils.getDistributionVersion(project)}-ClientAPI-Java-src.zip")
    }

    private void createJavaDocs()
    {
        project.copy({CopySpec copy ->
            copy.from project.project(":remoteapi:java").tasks.fatJar
            copy.into javaDir
        })
        project.project(":remoteapi:java").configurations.compile.addToAntBuilder(ant, "zipfileset", FileCollection.AntType.FileSet)
        ant.zip(destfile: getJavaClientApiFile()) {
            zipfileset(dir: project.project(":remoteapi:java").tasks.javadoc.destinationDir, prefix: "doc")
            zipfileset(file:"${project.project(":remoteapi:java").projectDir}/README.html")
            zipfileset(file:"${project.project(":remoteapi:java").tasks.fatJar.outputs.getFiles().asPath}")
        }

        ant.zip(destfile: getJavaClientApiSrcFile()) {
            zipfileset(dir: "${project.project(":remoteapi:java").projectDir}/src")
        }
    }

    private void createJdbcApi()
    {
        project.copy({CopySpec copy ->
            copy.from project.project(":remoteapi:labkey-api-jdbc").tasks.fatJar
            copy.into javaJdbcDir
        })
    }

    @OutputFiles
    List<File> getJavascriptDocsFiles()
    {
        List<File> docsOutput = new ArrayList<>()
        docsOutput.add(getJavascriptDocsFile("zip"))
        docsOutput.add(getJavascriptDocsFile("tar.gz"))
        return docsOutput
    }

    private File getJavascriptDocsFile(String extension)
    {
        return new File("${javascriptDir}/${getJavascriptDocsPrefix()}.${extension}")
    }

    private String getJavascriptDocsPrefix()
    {
        return "LabKey${BuildUtils.getDistributionVersion(project)}-${CLIENT_API_JSDOC}"
    }

    private void createClientApiDocs()
    {
        String prefix = getJavascriptDocsPrefix()
        ant.zip(destfile: getJavascriptDocsFile("zip")){
            zipfileset(dir: apiDocsBuildDir, prefix: prefix)
        }

        ant.tar(destfile: getJavascriptDocsFile("tar.gz"),
                longfile:"gnu",
                compression: "gzip") {
            tarfileset(dir: apiDocsBuildDir, prefix: prefix)
        }
    }

    @OutputFiles
    List<File> getXsdDocsFiles()
    {
        List<File> docsOutput = new ArrayList<>()
        docsOutput.add(getXsdDocsFile("zip"))
        docsOutput.add(getXsdDocsFile("tar.gz"))
        return docsOutput
    }
    private File getXsdDocsFile(String extension)
    {
        return new File("${xmlDir}/${getXsdDocsPrefix()}.${extension}")
    }

    private String getXsdDocsPrefix()
    {
        return "LabKey${BuildUtils.getDistributionVersion(project)}-${XML_SCHEMA_DOC}"
    }


    private void createXsdDocs()
    {

        String prefix = getXsdDocsPrefix()
        ant.zip(destfile: getXsdDocsFile("zip")) {
            zipfileset(dir: xsdDocsBuildDir, prefix: prefix)
        }

        ant.tar(destfile: getXsdDocsFile("tar.gz"),
                longfile:"gnu",
                compression: "gzip") {
            tarfileset(dir: xsdDocsBuildDir, prefix: prefix)
        }
    }

    private void createTeamCityArchives()
    {
        //Create a stable file names so that TeamCity can serve it up directly through its own UI
        ant.zip(destfile:"${project.dist.dir}/TeamCity-ClientAPI-Java-Docs.zip") {
            zipfileset(dir: project.project(":remoteapi:java").tasks.javadoc.destinationDir)
        }

        ant.zip(destfile: "${project.dist.dir}/TeamCity-${CLIENT_API_JSDOC}.zip") {
            zipfileset(dir: apiDocsBuildDir)
        }

        ant.zip(destfile: "${project.dist.dir}/TeamCity-${XML_SCHEMA_DOC}.zip") {
            zipfileset(dir: xsdDocsBuildDir)
        }
    }
}
