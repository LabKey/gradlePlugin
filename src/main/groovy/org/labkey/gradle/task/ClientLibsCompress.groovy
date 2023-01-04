/*
 * Copyright (c) 2016-2017 LabKey Corporation
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

import com.yahoo.platform.yui.compressor.CssCompressor
import com.yahoo.platform.yui.compressor.JavaScriptCompressor
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.tuple.Pair
import org.apache.tools.ant.util.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.ClientLibraries
import org.labkey.gradle.plugin.NpmRun
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.labkey.gradle.util.BuildUtils
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors

/**
 * Class for compressing javascript and css files using the yuicompressor classes.
 */
class ClientLibsCompress extends DefaultTask
{
    public static final String LIB_XML_EXTENSION = ".lib.xml"

    protected File workingDir = new File((String) project.labkey.explodedModuleWebDir)

   // This returns the libXml files from the project directory (the actual input files)
    @InputFiles
    FileTree xmlFiles
    private List<File> inputFiles = null
    private List<File> outputFiles = null
    private List<File> outputDirs = null

    /**
     * Creates a map between the individual .lib.xml files and the importers used to parse these files and
     * extract the css and javascript files that are referenced.
     * @return map between the file and the importer
     */
    private Map<File, XmlImporter> getImporterMap()
    {
        Map<File, XmlImporter> importerMap = new HashMap<>()
        xmlFiles.files.each() {
            File file ->
                importerMap.put(file, parseXmlFile(getSourceDir(file), file))
        }
        return importerMap;
    }

    static File getMinificationDir(Project project)
    {
        return project.project(BuildUtils.getMinificationProjectPath(project.gradle))
                .file("modules/${project.name}")
    }

    static File getSourceDir(File libXmlFile)
    {
        String absolutePath = libXmlFile.getAbsolutePath();
        int endIndex = absolutePath.lastIndexOf("webapp${File.separator}")
        if (endIndex >= 0)
            endIndex += 6;
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
     * Input files include:
     * - .lib.xml files
     * - css files referenced in the .lib.xml files
     * - js files referenced in the .lib.xml files
     * @return list of all the .lib.xml files and the (internal) files referenced in the .lib.xml files
     */
    @InputFiles
    List<File> getInputFiles()
    {
        if (inputFiles == null)
        {
            inputFiles = new ArrayList<>()
            inputFiles.addAll(xmlFiles)

            getImporterMap().entrySet().each { Map.Entry<File, XmlImporter> entry ->
                if (entry.value.getCssFiles().size() > 0)
                {
                    inputFiles.addAll(entry.value.getCssFiles())
                }
                if (entry.value.getJavascriptFiles().size() > 0)
                {
                    inputFiles.addAll(entry.value.getJavascriptFiles())
                }
            }
        }
        return inputFiles
    }

    File getMinificationWorkingDir(File libXmlFile)
    {
        return new File(getMinificationDir(project), "${libXmlFile.name.substring(0, libXmlFile.name.length() - LIB_XML_EXTENSION.length())}")
    }

    @OutputFiles
    List<File> getOutputFiles()
    {
        if (outputFiles == null)
        {
            outputFiles = new ArrayList<>()

            getImporterMap().entrySet().each { Map.Entry<File, XmlImporter> entry ->
                // The output file will be in the working directory not in the source directory used when parsing the file.
                String fileName = entry.key.getAbsolutePath()
                fileName = fileName.replace(entry.value.sourceDir.getAbsolutePath(), workingDir.getAbsolutePath())
                File workingFile = project.file(fileName)
                if (entry.value.getCssFiles().size() > 0)
                {
                    outputFiles.add(getOutputFile(workingFile, "min", "css"))
                    if (LabKeyExtension.isDevMode(project))
                        outputFiles.add(getOutputFile(workingFile, "min", "css.gz"))
                    outputFiles.add(getOutputFile(workingFile, "combined", "css"))
                }
                if (entry.value.getJavascriptFiles().size() > 0)
                {
                    outputFiles.add(getOutputFile(workingFile, "min", "js"))
                    if (LabKeyExtension.isDevMode(project))
                        outputFiles.add(getOutputFile(workingFile, "min", "js.gz"))
                    outputFiles.add(getOutputFile(workingFile, "combined", "js"))
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

            if (ClientLibraries.useNpmMinifier(project)) {
                getImporterMap().entrySet().each { Map.Entry<File, XmlImporter> entry ->
                    {
                        if (entry.value.doCompile && entry.value.hasFilesToCompress())
                            outputDirs.add(getMinificationWorkingDir(entry.key))
                    }
                }
            }
        }
        return outputDirs
    }

    @TaskAction
    void compressAllFiles()
    {
        FileTree libXmlFiles = xmlFiles
        Map<File, XmlImporter> importerMap = getImporterMap();
        libXmlFiles.files.each() {
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
            try
            {
                if (ClientLibraries.useNpmMinifier(project))
                {
                    minifyViaNpm(xmlFile, importer)
                }
                else
                {
                    if (importer.getJavascriptFiles().size() > 0)
                        compileScriptsViaYui(xmlFile, importer.getJavascriptFiles(), "js")
                    if (importer.getCssFiles().size() > 0)
                        compileScriptsViaYui(xmlFile, importer.getCssFiles(), "css")
                }
            }
            catch (Exception e)
            {
                throw new SAXException(e);
            }
        }
        else
        {
            this.logger.info("No compile necessary for ${xmlFile}");
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
            XmlImporter importer = new XmlImporter(xmlFile, sourceDir)
            parser.parse(xmlFile, importer)
            return importer
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }


    private String getNpmCommand()
    {
        Project minProject = project.project(BuildUtils.getMinificationProjectPath(project.gradle))
        return "${minProject.projectDir}/.gradle/npm/npm-v${minProject.npmVersion}${SystemUtils.IS_OS_WINDOWS ? '' : '/bin'}/${NpmRun.getNpmCommand()}"
    }

    void minifyViaNpm(File xmlFile, XmlImporter importer)
    {
        if (importer.hasFilesToCompress()) {
            File cssFile = concatenateCssFiles(xmlFile, importer.cssFiles)
            Pair<File, File> minFiles = createPackageJson(xmlFile, importer.javascriptFiles, cssFile)
            if (importer.hasJavascriptFiles()) {
                project.logger.quiet("Compressing Javascript files for ${xmlFile}")
                project.ant.exec(
                    executable: getNpmCommand(),
                    dir: getMinificationWorkingDir(xmlFile)
                )
                    {
                        arg(line: "run minify-js")
                    }
                project.logger.quiet("DONE Compressing Javascript files as ${minFiles.left}")
                compressFile(minFiles.left)
            }
            if (importer.hasCssFiles()) {
                project.logger.quiet("Compressing css files for ${xmlFile}")
                project.ant.exec(
                    executable: getNpmCommand(),
                    dir: getMinificationWorkingDir(xmlFile)
                )
                    {
                        arg(line: "run minify-css")
                    }
                project.logger.quiet("DONE Compressing css files as ${minFiles.right}")
                compressFile(minFiles.right)
            }
        }
    }

    Pair<File, File> createPackageJson(File xmlFile, Set<File> jsFiles, File allCssFile)
    {
        File jsMinFile = null
        File cssMinFile = null

        File sourceDir = getSourceDir(xmlFile)
        File workingFile = new File(xmlFile.getAbsolutePath().replace(sourceDir.getAbsolutePath(), workingDir.getAbsolutePath()))

        String jsFileNames = jsFiles.stream().map(jsFile -> jsFile.getAbsolutePath()).collect(Collectors.joining(" "))
        File packageJson = new File(getMinificationWorkingDir(xmlFile), "package.json")
        project.logger.info("Creating ${packageJson} for ${xmlFile.getAbsolutePath()}")
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
        if (!jsFiles.isEmpty()) {
            jsMinFile = getOutputFile(workingFile, "min", "js")
            buffer.append(
                    "    \"minify-js\": \"terser ${jsFileNames} -o ${jsMinFile.getAbsolutePath()}\""
            )
            comma = ",\n"
        }
        if (allCssFile != null) {
            cssMinFile = getOutputFile(workingFile, "min", "css")
            buffer.append(comma)
            buffer.append(
                    "    \"minify-css\": \"postcss ${allCssFile.getAbsolutePath()} --ext min.css --dir ${workingDir.getAbsolutePath()}\""
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


    File concatenateCssFiles(File xmlFile, Set<File> srcFiles)
    {
        if (srcFiles.isEmpty())
            return null;


        File concatFile = new File(getMinificationWorkingDir(xmlFile), getNewExtensionFileName(xmlFile, null, "css"))
        this.logger.info("Concatenating css files into single file ${concatFile}")
        concatenateFiles(srcFiles, concatFile)
        return concatFile
    }

    void compressFile(File file)
    {
        if (!LabKeyExtension.isDevMode(project))
        {
            this.logger.info("Compressing " + file);
            project.ant.gzip(
                    src: file,
                    destfile: "${file}.gz"
            )
        }
    }

    void compileScriptsViaYui(File xmlFile, Set<File> srcFiles, String extension) throws IOException, InterruptedException
    {
        File sourceDir = getSourceDir(xmlFile)
        File workingFile = new File(xmlFile.getAbsolutePath().replace(sourceDir.getAbsolutePath(), workingDir.getAbsolutePath()))
        File minFile = getOutputFile(workingFile, "min", extension);

        this.logger.info("Concatenating " + extension + " files into single file");
        File concatFile = getOutputFile(workingFile, "combined", extension);
        concatenateFiles(srcFiles, concatFile);

        this.logger.info("Minifying " + extension + " files with YUICompressor");
        minFile.delete();

        minifyFile(concatFile, minFile);

        concatFile.delete();
        compressFile(minFile)
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
        return new File(xmlFile.getParentFile(), getNewExtensionFileName(xmlFile, token, ex));
    }

    private static void concatenateFiles(Set<File> files, File output)
    {
        try
        {
            output.createNewFile();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
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

                    String line = readBuff.readLine();

                    while (line != null)
                    {
                        saveAs.println(line);
                        line = readBuff.readLine();
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

    private static void minifyFile(File srcFile, File destFile) throws IOException
    {
        if (srcFile.getName().endsWith("js"))
        {
            srcFile.withReader { reader ->
                JavaScriptCompressor compressor = new JavaScriptCompressor(reader, null)

                destFile.withWriter { writer ->
                    compressor.compress(writer, null, -1, true, false, false, false)
                }
            }
        }
        else
        {
            srcFile.withReader { reader ->
                CssCompressor compressor = new CssCompressor(reader)
                destFile.withWriter { writer ->
                    compressor.compress(writer, -1)
                }
            }
        }
    }

    private class XmlImporter extends DefaultHandler
    {
        private boolean withinScriptsTag = false
        private File xmlFile
        private File sourceDir
        private LinkedHashSet<File> javascriptFiles = new LinkedHashSet<>()
        private LinkedHashSet<File> cssFiles = new LinkedHashSet<>()
        private boolean doCompile = true

        XmlImporter(File xml, File sourceDir)
        {
            xmlFile = xml
            this.sourceDir = sourceDir
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
                    doCompile = Boolean.parseBoolean(attributes.getValue("compileInProductionMode"));
                }
                withinScriptsTag = true;
            }
            if (withinScriptsTag && "script".equals(localName))
            {
                String path = attributes.getValue("path");
                File scriptFile = new File(sourceDir, path);
                if (!scriptFile.exists())
                {
                    if (isExternalScript(path))
                    {
                        throw new RuntimeException("ERROR: External scripts (e.g. https://.../script.js) cannot be declared in library definition. Consider making it a <dependency>.");
                    }
                    else
                    {
                        throw new RuntimeException("ERROR: Unable to find script file: " + scriptFile + " from library: " + xmlFile);
                    }
                }
                else
                {
                    //linux will be case-sensitive, so we proactively throw errors on any filesystem
                    try
                    {
                        File f = FileUtils.getFileUtils().normalize(scriptFile.getPath());
                        if( !scriptFile.getCanonicalFile().getName().equals(f.getName()))
                        {
                            throw new RuntimeException("File must be a case-sensitive match. Found: " + scriptFile.getAbsolutePath() + ", expected: " + scriptFile.getCanonicalPath());
                        }
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }

                    if (scriptFile.getName().endsWith(".js"))
                        javascriptFiles.add(scriptFile);
                    else if (scriptFile.getName().endsWith(".css"))
                        cssFiles.add(scriptFile);
                    else
                        this.logger.info("Unknown file extension, ignoring: " + scriptFile.getName());
                }
            }
        }

        void endElement(String uri, String localName, String qName) throws SAXException
        {
            if ("library".equals(localName))
                withinScriptsTag = false;
        }

        private boolean isExternalScript(String path)
        {
            return path != null && (path.contains("http://") || path.contains("https://"));
        }
    }
}
