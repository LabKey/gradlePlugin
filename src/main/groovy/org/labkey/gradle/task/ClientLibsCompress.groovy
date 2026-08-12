/*
 * Copyright (c) 2016-2026 LabKey Corporation
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

import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.tuple.Pair
import org.apache.tools.ant.util.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.work.DisableCachingByDefault
import org.labkey.gradle.plugin.NpmRun
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.util.BuildUtils
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

import javax.inject.Inject
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors
import java.util.zip.GZIPOutputStream

/**
 * Class for compressing javascript and css files
 */

@CacheableTask
abstract class ClientLibsCompress extends DefaultTask
{
    public static final String LIB_XML_EXTENSION = ".lib.xml"

    @Inject abstract ExecOperations getExec()

   // This returns the libXml files from the project directory (the actual input files)
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getXmlFiles()

    private List<File> inputFiles = null
    private List<File> outputFiles = null
    private List<File> outputDirs = null

    @Input
    final abstract Property<Boolean> isDevMode = project.objects.property(Boolean).convention(LabKeyExtension.isDevMode(project))

    @Input
    final abstract Property<String> nodeVersion = project.objects.property(String).convention(project.hasProperty("nodeVersion") ? project.nodeVersion : "")

    // The directory the minified files are written to, which is also the directory used to derive the output file names
    @Internal
    final abstract Property<String> workingDirPath = project.objects.property(String).convention(new File((String) project.labkey.explodedModuleWebDir).getAbsolutePath())

    // The directory in the minification project where the package.json files and concatenated sources are written
    @Internal
    final abstract DirectoryProperty minificationDir = project.objects.directoryProperty().fileValue(getMinificationDir(project))

    // The directory where the node distribution is unpacked by the minification project's npmInstall task
    @Internal
    final abstract DirectoryProperty nodeJsDir = project.objects.directoryProperty().fileValue(findNodeJsDir(project))

    // Used only for error reporting when the node executable cannot be found
    @Internal
    final abstract Property<String> minificationProjectPath = project.objects.property(String).convention(BuildUtils.getMinificationProjectPath(project.gradle))

    // Missing files are simply not snapshotted, so there is no need to filter for existence here
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    final abstract ConfigurableFileCollection npmPackageFiles = project.objects.fileCollection().from(findNpmPackageFiles(project))

    private static File findNodeJsDir(Project project)
    {
        if (!BuildUtils.haveMinificationProject(project.gradle))
            return null
        Project minProject = project.project(BuildUtils.getMinificationProjectPath(project.gradle))
        return new File("${minProject.projectDir}/.gradle/nodejs")
    }

    private static List<File> findNpmPackageFiles(Project project)
    {
        if (!BuildUtils.haveMinificationProject(project.gradle))
            return Collections.emptyList()
        Project minProject = project.project(BuildUtils.getMinificationProjectPath(project.gradle))
        return List.of(
                new File("${minProject.projectDir}/package.json"),
                new File("${minProject.projectDir}/package-lock.json")
        )
    }

    /**
     * Creates a map between the individual .lib.xml files and the importers used to parse these files and
     * extract the css and javascript files that are referenced.
     * @return map between the file and the importer
     */
    @Internal
    Map<File, XmlImporter> getImporterMap()
    {
        Map<File, XmlImporter> importerMap = new LinkedHashMap<>()
        xmlFiles.files.each() {
            File file ->
                importerMap.put(file, parseXmlFile(getSourceDir(file), file))
        }
        return importerMap
    }

    static File getMinificationDir(Project project)
    {
        return project.project(BuildUtils.getMinificationProjectPath(project.gradle))
                .file("modules/${project.name}")
    }

    static File getSourceDir(File libXmlFile)
    {
        String absolutePath = libXmlFile.getAbsolutePath()
        int endIndex = absolutePath.lastIndexOf("webapp${File.separator}")
        if (endIndex >= 0)
            endIndex += 6
        else
        {
            endIndex = absolutePath.lastIndexOf("web${File.separator}")
            if (endIndex >= 0)
                endIndex += 3
        }
        if (endIndex < 0)
            throw new Exception("File ${libXmlFile} not in webapp or web directory.")
        return new File(absolutePath.substring(0, endIndex))
    }

    /**
     * Input files includes:
     * - css files referenced in the .lib.xml files
     * - js files referenced in the .lib.xml files
     * @return list of all the (internal) files referenced in the .lib.xml files (the xml files are designated as input above)
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    List<File> getInputFiles()
    {
        if (inputFiles == null) {
           inputFiles = new ArrayList<>()

            getImporterMap().entrySet().each { Map.Entry<File, XmlImporter> entry ->
                if (entry.value.getCssFiles().size() > 0) {
                    inputFiles.addAll(entry.value.getCssFiles())
                }
                if (entry.value.getJavascriptFiles().size() > 0) {
                    inputFiles.addAll(entry.value.getJavascriptFiles())
                }
            }
        }
        return inputFiles
    }

    File getMinificationWorkingDir(File libXmlFile)
    {
        return new File(minificationDir.get().asFile, "${libXmlFile.name.substring(0, libXmlFile.name.length() - LIB_XML_EXTENSION.length())}")
    }

    @OutputFiles
    List<File> getOutputFiles()
    {
        if (outputFiles == null)
        {
            outputFiles = new ArrayList<>()

            getImporterMap().entrySet().each { Map.Entry<File, XmlImporter> entry ->
                if (entry.value.doCompile) {
                    // The output file will be in the working directory not in the source directory used when parsing the file.
                    String fileName = entry.key.getAbsolutePath()
                    fileName = fileName.replace(entry.value.sourceDir.getAbsolutePath(), workingDirPath.get())
                    File workingFile = new File(fileName)
                    if (entry.value.getCssFiles().size() > 0) {
                        outputFiles.add(getOutputFile(workingFile, "min", "css"))
                        if (!isDevMode.get())
                            outputFiles.add(getOutputFile(workingFile, "min", "css.gz"))
                    }
                    if (entry.value.getJavascriptFiles().size() > 0) {
                        outputFiles.add(getOutputFile(workingFile, "min", "js"))
                        if (!isDevMode.get())
                            outputFiles.add(getOutputFile(workingFile, "min", "js.gz"))
                    }
                }
            }
        }
        return outputFiles
    }

    @OutputDirectories
    List<File> getOutputDirs()
    {
        if (outputDirs == null) {
            outputDirs = new ArrayList<>()
            getImporterMap().entrySet().each { Map.Entry<File, XmlImporter> entry ->
                {
                    if (entry.value.doCompile && entry.value.hasFilesToCompress())
                        outputDirs.add(getMinificationWorkingDir(entry.key))
                }
            }
        }
        return outputDirs
    }

    @TaskAction
    void compressAllFiles()
    {
        Map<File, XmlImporter> importerMap = getImporterMap()
        xmlFiles.files.each() {
            File file -> compressSingleFile(file, importerMap.get(file))
        }
    }

    void compressSingleFile(File xmlFile, XmlImporter importer)
    {
        if (importer == null)
        {
            this.logger.warn("No importer found for file ${xmlFile}")
        }
        else if (importer.doCompile)
        {
            minifyViaNpm(xmlFile, importer)
        }
        else
        {
            this.logger.info("No compile necessary for ${xmlFile}")
        }
    }

    XmlImporter parseXmlFile(File sourceDir, File xmlFile)
    {
        try
        {
            SAXParserFactory factory = SAXParserFactory.newInstance()
            factory.setNamespaceAware(true)
            factory.setValidating(true)
            SAXParser parser = factory.newSAXParser()
            // we pass in the source directory here because this directory is used for constructing
            // the destination files
            XmlImporter importer = new XmlImporter(xmlFile, sourceDir, logger)
            parser.parse(xmlFile, importer)
            return importer
        }
        catch (Exception e)
        {
            throw new RuntimeException(e)
        }
    }

    @Internal
    String getNodeExecutableDir()
    {
        String nodeFilePrefix = "node-v${nodeVersion.get()}-"
        File nodeDir = nodeJsDir.getAsFile().getOrNull()
        File[] nodeFiles = nodeDir == null ? null : nodeDir.listFiles({ File file -> file.name.startsWith(nodeFilePrefix) } as FileFilter)
        if (nodeFiles != null && nodeFiles.length > 0)
            return "${nodeFiles[0].getAbsolutePath()}${SystemUtils.IS_OS_WINDOWS ? '' : '/bin'}"
        else
            return null
    }

    void minifyViaNpm(File xmlFile, XmlImporter importer)
    {
        if (importer.hasFilesToCompress()) {
            String executableDir = getNodeExecutableDir()
            if (executableDir == null)
                throw new GradleException("Could not find expected files in ${minificationProjectPath.get()} project")

            Pair<File, File> minFiles = createPackageJson(xmlFile, importer)
            if (importer.hasJavascriptFiles()) {
                logger.info("Compressing Javascript files for ${xmlFile} with ${executableDir} in ${getMinificationWorkingDir(xmlFile)}")
                runNpmScript(xmlFile, "minify-js", executableDir, "Javascript")
                logger.debug("DONE Compressing Javascript files as ${minFiles.left}")
                compressFile(minFiles.left)
            }
            if (importer.hasCssFiles()) {
                logger.info("Compressing css files for ${xmlFile}")
                runNpmScript(xmlFile, "minify-css", executableDir, "css")
                logger.debug("DONE Compressing css files as ${minFiles.right}")
                compressFile(minFiles.right)
            }
        }
    }

    private void runNpmScript(File xmlFile, String scriptName, String executableDir, String fileType)
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        ByteArrayOutputStream error = new ByteArrayOutputStream()
        ExecResult result = exec.exec({ ExecSpec spec ->
            spec.executable = "${executableDir}/${NpmRun.getNpmCommand()}"
            spec.args("run", scriptName)
            spec.workingDir = getMinificationWorkingDir(xmlFile)
            spec.environment("PATH", "${executableDir}${File.pathSeparator}${System.getenv("PATH")}")
            spec.standardOutput = output
            spec.errorOutput = error
            // we report the failure ourselves, with the captured output, below
            spec.ignoreExitValue = true
        })
        logger.debug("${path} ${xmlFile} npm text ${output}")
        logger.debug("${path} ${xmlFile} npm error ${error}")
        logger.debug("${path} ${xmlFile} npm exitValue ${result.exitValue}")
        if (result.exitValue != 0)
            throw new GradleException("Error compressing ${fileType} files for ${xmlFile}. Exit code ${result.exitValue}.\n Output: ${output}.\n Error: ${error} ")
    }

    static String escapeBackslashPaths(String path)
    {
        return path.replaceAll("\\\\", "\\\\\\\\")
    }

    Pair<File, File> createPackageJson(File xmlFile, XmlImporter importer)
    {
        File jsMinFile = null
        File cssMinFile = null

        File sourceDir = getSourceDir(xmlFile)
        File workingFile = new File(xmlFile.getAbsolutePath().replace(sourceDir.getAbsolutePath(), workingDirPath.get()))

        File packageJson = new File(getMinificationWorkingDir(xmlFile), "package.json")
        logger.info("Creating ${packageJson} for ${xmlFile.getAbsolutePath()}")
        String sanitizedName = xmlFile.name.substring(0, xmlFile.name.length()-LIB_XML_EXTENSION.length())
        packageJson.createNewFile()
        StringBuffer buffer = new StringBuffer("")
        buffer.append("")
        buffer.append("{\n" +
                "  \"name\": \"minify-${sanitizedName}\",\n" +
                "  \"version\": \"0.1.0\",\n" +
                "  \"private\": true,\n" +
                "  \"scripts\": {\n")
        String comma = "\n"
        if (importer.hasJavascriptFiles()) {
            jsMinFile = getOutputFile(workingFile, "min", "js")
            String jsFileNames = importer.hasJavascriptFiles() ? importer.javascriptFiles.stream().map(jsFile ->
                    escapeBackslashPaths(jsFile.getAbsolutePath()))
                    .collect(Collectors.joining(" ")) : null
            // max command line length for Windows is ~8200 characters, but no need to push the edge here.
            // We avoid concatenating the files to make things faster when we can.
            if (jsFileNames.length() > 7000) {
                File allJsFile = concatenateFilesForNpm(xmlFile, importer.javascriptFiles, "js")
                buffer.append(
                        "    \"minify-js\": \"terser ${escapeBackslashPaths(allJsFile.getAbsolutePath())} -o ${escapeBackslashPaths(jsMinFile.getAbsolutePath())}\""
                )
            } else {
                buffer.append(
                        "    \"minify-js\": \"terser ${jsFileNames} -o ${escapeBackslashPaths(jsMinFile.getAbsolutePath())}\""
                )
            }

            comma = ",\n"
        }
        if (importer.hasCssFiles()) {
            File allCssFile = concatenateFilesForNpm(xmlFile, importer.cssFiles, "css")
            cssMinFile = getOutputFile(workingFile, "min", "css")
            buffer.append(comma)
            buffer.append(
                    "    \"minify-css\": \"postcss ${escapeBackslashPaths(allCssFile.getAbsolutePath())} --ext min.css --dir ${escapeBackslashPaths(cssMinFile.parentFile.getAbsolutePath())}\""
            )
        }
        buffer.append(
                "\n" +
                "  }\n" +
                "}")
        PrintWriter writer = null
        try {
            writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(packageJson), StandardCharsets.UTF_8)))
            writer.println(buffer.toString())
        }
        finally
        {
            IOUtils.closeQuietly(writer)
        }
        return Pair.of(jsMinFile, cssMinFile)
    }

    File concatenateFilesForNpm(File xmlFile, Set<File> srcFiles, String extension)
    {
        if (srcFiles.isEmpty())
            return null


        File concatFile = new File(getMinificationWorkingDir(xmlFile), getNewExtensionFileName(xmlFile, null, extension))
        this.logger.info("Concatenating ${extension} files into single file ${concatFile}")
        concatenateFiles(srcFiles, concatFile)
        return concatFile
    }

    void compressFile(File file)
    {
        if (!isDevMode.get())
        {
            this.logger.info("Compressing " + file)
            new FileInputStream(file).withStream { InputStream input ->
                new GZIPOutputStream(new FileOutputStream("${file}.gz")).withStream { OutputStream output ->
                    IOUtils.copy(input, output)
                }
            }
        }
    }

    static String getNewExtensionFileName(File xmlFile, String token, String ex)
    {
        String replacement =  "." + ex
        if (token != null)
            replacement = "." + token + replacement
        return xmlFile.getName().replaceAll(LIB_XML_EXTENSION, replacement)
    }

    static File getOutputFile(File xmlFile, String token, String ex)
    {
        return new File(xmlFile.getParentFile(), getNewExtensionFileName(xmlFile, token, ex))
    }

    private static void concatenateFiles(Set<File> files, File output)
    {
        try
        {
            output.createNewFile()
        }
        catch (IOException e)
        {
            throw new RuntimeException(e)
        }

        PrintWriter saveAs = null
        try
        {
            saveAs = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), StandardCharsets.UTF_8)))

            for (File f : files)
            {
                BufferedReader readBuff = null
                try
                {
                    readBuff = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))

                    String line = readBuff.readLine()

                    while (line != null)
                    {
                        saveAs.println(line)
                        line = readBuff.readLine()
                    }
                }
                finally
                {
                    IOUtils.closeQuietly(readBuff)
                }

            }
        }
        finally
        {
            IOUtils.closeQuietly(saveAs)
        }
    }

    private static class XmlImporter extends DefaultHandler
    {
        private boolean withinScriptsTag = false
        private File xmlFile
        private File sourceDir
        private Logger logger
        private LinkedHashSet<File> javascriptFiles = new LinkedHashSet<>()
        private LinkedHashSet<File> cssFiles = new LinkedHashSet<>()
        private boolean doCompile = true

        XmlImporter(File xml, File sourceDir, Logger logger)
        {
            xmlFile = xml
            this.sourceDir = sourceDir
            this.logger = logger
        }

        boolean hasFilesToCompress()
        {
            return hasJavascriptFiles() || hasCssFiles()
        }

        boolean hasJavascriptFiles()
        {
            return !javascriptFiles.isEmpty()
        }

        LinkedHashSet<File> getJavascriptFiles()
        {
            return javascriptFiles
        }

        boolean hasCssFiles()
        {
            return !cssFiles.isEmpty()
        }

        LinkedHashSet<File> getCssFiles()
        {
            return cssFiles
        }

        boolean getDoCompile()
        {
            return doCompile
        }

        void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
        {
            if ("library".equals(localName))
            {
                if (attributes.getValue("compileInProductionMode") != null)
                {
                    doCompile = Boolean.parseBoolean(attributes.getValue("compileInProductionMode"))
                }
                withinScriptsTag = true
            }
            if (withinScriptsTag && "script".equals(localName))
            {
                String path = attributes.getValue("path")
                File scriptFile = new File(sourceDir, path)
                if (!scriptFile.exists())
                {
                    if (isExternalScript(path))
                    {
                        throw new RuntimeException("ERROR: External scripts (e.g. https://.../script.js) cannot be declared in library definition. Consider making it a <dependency>.")
                    }
                    else
                    {
                        throw new RuntimeException("ERROR: Unable to find script file: " + scriptFile + " from library: " + xmlFile)
                    }
                }
                else
                {
                    //linux will be case-sensitive, so we proactively throw errors on any filesystem
                    try
                    {
                        File f = FileUtils.getFileUtils().normalize(scriptFile.getPath())
                        if( !scriptFile.getCanonicalFile().getName().equals(f.getName()))
                        {
                            throw new RuntimeException("File must be a case-sensitive match. Found: " + scriptFile.getAbsolutePath() + ", expected: " + scriptFile.getCanonicalPath())
                        }
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e)
                    }

                    if (scriptFile.getName().endsWith(".js"))
                        javascriptFiles.add(scriptFile)
                    else if (scriptFile.getName().endsWith(".css"))
                        cssFiles.add(scriptFile)
                    else
                        this.logger.info("Unknown file extension, ignoring: " + scriptFile.getName())
                }
            }
        }

        void endElement(String uri, String localName, String qName) throws SAXException
        {
            if ("library".equals(localName))
                withinScriptsTag = false
        }

        private boolean isExternalScript(String path)
        {
            return path != null && (path.contains("http://") || path.contains("https://"))
        }
    }
}
