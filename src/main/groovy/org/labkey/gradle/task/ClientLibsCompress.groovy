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
import org.apache.tools.ant.util.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.extension.LabKeyExtension
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory
import java.nio.charset.StandardCharsets
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
    protected Map<File, XmlImporter> _importerMap = null

    /**
     * Creates a map between the individual .lib.xml files and the importers used to parse these files and
     * extract the css and javascript files that are referenced.
     * @return map between the file and the importer
     */
    private Map<File, XmlImporter> getImporterMap()
    {
        if (_importerMap == null)
        {
            _importerMap = new HashMap<>()
            xmlFiles.files.each() {
                File file ->
                    _importerMap.put(file, parseXmlFile(getSourceDir(file), file))
            }
        }
        return _importerMap;
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
                if (entry.value.getJavaScriptFiles().size() > 0)
                {
                    inputFiles.addAll(entry.value.getJavaScriptFiles())
                }
            }
        }
        return inputFiles
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
                File workingFile = new File(fileName)
                if (entry.value.getCssFiles().size() > 0)
                {
                    outputFiles.add(getOutputFile(workingFile, "min", "css"))
                    outputFiles.add(getOutputFile(workingFile, "combined", "css"))
                }
                if (entry.value.getJavaScriptFiles().size() > 0)
                {
                    outputFiles.add(getOutputFile(workingFile, "min", "js"))
                    outputFiles.add(getOutputFile(workingFile, "combined", "js"))
                }
            }
        }
        return outputFiles
    }

    @TaskAction
    void compressAllFiles()
    {
        FileTree libXmlFiles = xmlFiles
        libXmlFiles.files.each() {
            File file -> compressSingleFile(file)
        }
    }

    void compressSingleFile(File xmlFile)
    {
        XmlImporter importer = getImporterMap().get(xmlFile)
        if (importer.doCompile)
        {
            try
            {
                if (importer.getJavaScriptFiles().size() > 0)
                    compileScripts(xmlFile, importer.getJavaScriptFiles(), "js");
                if (importer.getCssFiles().size() > 0)
                    compileScripts(xmlFile, importer.getCssFiles(), "css");
            }
            catch (Exception e)
            {
                throw new SAXException(e);
            }
        }
        else
        {
            project.logger.info("No compile necessary");
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


    void compileScripts(File xmlFile, Set<File> srcFiles, String extension) throws IOException, InterruptedException
    {
        File sourceDir = getSourceDir(xmlFile)
        File workingFile = new File(xmlFile.getAbsolutePath().replace(sourceDir.getAbsolutePath(), workingDir.getAbsolutePath()))
        File minFile = getOutputFile(workingFile, "min", extension);

        project.logger.info("Concatenating " + extension + " files into single file");
        File concatFile = getOutputFile(workingFile, "combined", extension);
        concatenateFiles(srcFiles, concatFile);

        project.logger.info("Minifying " + extension + " files with YUICompressor");
        minFile.delete();

        minifyFile(concatFile, minFile);

        concatFile.delete();

        if (!LabKeyExtension.isDevMode(project))
        {
            project.logger.info("Compressing " + extension + " files");
            project.ant.gzip(
                    src: minFile,
                    destfile: "${minFile.toString()}.gz"
            )
        }
    }

    static File getOutputFile(File xmlFile, String token, String ex)
    {
        return new File(xmlFile.getParentFile(), xmlFile.getName().replaceAll(LIB_XML_EXTENSION, "." + token + "." + ex));
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
        private LinkedHashSet<File> javaScriptFiles = new LinkedHashSet<>()
        private LinkedHashSet<File> cssFiles = new LinkedHashSet<>()
        private boolean doCompile = true

        XmlImporter(File xml, File sourceDir)
        {
            xmlFile = xml
            this.sourceDir = sourceDir
        }

        LinkedHashSet<File> getJavaScriptFiles()
        {
            return javaScriptFiles
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
                        javaScriptFiles.add(scriptFile);
                    else if (scriptFile.getName().endsWith(".css"))
                        cssFiles.add(scriptFile);
                    else
                        project.logger.info("Unknown file extension, ignoring: " + scriptFile.getName());
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
