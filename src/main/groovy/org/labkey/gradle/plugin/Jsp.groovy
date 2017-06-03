package org.labkey.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar
import org.labkey.gradle.plugin.extension.JspCompileExtension
import org.labkey.gradle.task.JspCompile2Java
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames

/**
 * Used to generate the jsp jar file for a module.
 */
class Jsp implements Plugin<Project>
{
    public static final String CLASSIFIER = "jsp"
    public static final String BASE_NAME_EXTENSION = "_jsp"

    static boolean isApplicable(Project project)
    {
        return !getJspFileTree(project).isEmpty()
    }

    private static FileTree getJspFileTree(Project project)
    {
        FileTree jspTree = project.fileTree("src").matching
                {
                    include('**/*.jsp')
                }
        jspTree += project.fileTree("resources").matching
                {
                    include("**/*.jsp")
                }
        return jspTree
    }

    @Override
    void apply(Project project)
    {
        project.apply plugin: 'java-base'
        project.extensions.create("jspCompile", JspCompileExtension)

        addConfiguration(project)
        addDependencies(project)
        addSourceSet(project)
        addJspTasks(project)
    }

    private void addSourceSet(Project project)
    {
        project.sourceSets
                {
                    jsp {
                        java {
                            srcDirs = ["${project.buildDir}/${project.jspCompile.tempDir}/classes"]
                        }
                        output.classesDir = "${project.buildDir}/${project.jspCompile.tempDir}/classes"
                    }
                }
    }

    private void addConfiguration(Project project)
    {
        project.configurations
                {
                    jspCompile
                    jsp
                }
        project.configurations.getByName('jspCompile') {
            resolutionStrategy {
                force "javax.servlet:servlet-api:${project.servletApiVersion}" // the version number here is sort of irrelevant until we use a repository other than the file system; it just needs to be something other than the old 2.4 version in external/libs/build
            }
        }
    }

    private void addDependencies(Project project)
    {
        project.dependencies
                {
                    jspCompile  'org.apache.tomcat:jasper',
                        'org.apache.tomcat:jsp-api',
                        'org.apache.tomcat:tomcat-juli'
                    jspCompile project.fileTree(dir: "${project.tomcatDir}/lib", includes: ['*.jar'])
                    BuildUtils.addLabKeyDependency(project: project, config: "jspCompile", depProjectPath: ":server:api", depVersion: project.labkeyVersion)
                    BuildUtils.addLabKeyDependency(project: project, config: "jspCompile", depProjectPath: ":server:internal", depVersion: project.labkeyVersion)
                    jspCompile project.files(project.tasks.jar)
                    if (project.hasProperty('apiJar'))
                        jspCompile project.files(project.tasks.apiJar)

                    jsp     'org.apache.tomcat:jasper',
                            'org.apache.tomcat:bootstrap',
                            'org.apache.tomcat:tomcat-juli'
                }
        // We need this declaration for IntelliJ to be able to find the .tld files, but if we include
        // it for the command line, there will be lots of warnings about .tld files on the classpath where
        // they don't belong ("CLASSPATH element .../labkey.tld is not a JAR.").  These warnings may appear if
        // building within IntelliJ but perhaps we can live with that (for now).
        if (BuildUtils.isIntellij())
            project.dependencies.add("compile", project.rootProject.tasks.copyTagLibsBase.inputs.files)
    }

    private void addJspTasks(Project project)
    {
        Task listJsps = project.task('listJsps', group: GroupNames.JSP)
        listJsps.doLast {
                    getJspFileTree(project).each ({
                        println it.absolutePath
                    })
                }

        Task copyJsps = project.task('copyJsp', group: GroupNames.JSP, type: Copy, description: "Copy jsp files to jsp compile directory",
                { CopySpec copy ->
                    copy.from 'src'
                    copy.into "${project.buildDir}/${project.jspCompile.tempDir}/webapp"
                    copy.include '**/*.jsp'
                })

        Task copyResourceJsps = project.task('copyResourceJsp', group: GroupNames.JSP, type: Copy, description: "Copy resource jsp files to jsp compile directory",
                { CopySpec copy ->
                    copy.from 'resources'
                    copy.into "${project.buildDir}/${project.jspCompile.tempDir}/webapp/org/labkey/${project.name}"
                    copy.include '**/*.jsp'
                })

        Task copyTags = project.task('copyTagLibs', group: GroupNames.JSP, type: Copy, description: "Copy the tag library (.tld) files to jsp compile directory",
                { CopySpec copy ->
                    copy.from "${project.rootProject.buildDir}/webapp"
                    copy.into "${project.buildDir}/${project.jspCompile.tempDir}/webapp"
                    copy.include 'WEB-INF/web.xml'
                    copy.include 'WEB-INF/*.tld'
                    copy.include 'WEB-INF/tags/**'
                })
        if (project.findProject(":server") != null)
            copyTags.dependsOn(project.rootProject.tasks.copyTagLibsBase)

        Task jspCompileTask = project.task('jsp2Java',
                group: GroupNames.JSP,
                type: JspCompile2Java,
                description: "compile jsp files into Java classes",
                {
                    inputs.file copyJsps
                    inputs.file copyResourceJsps
                    inputs.file copyTags
                    outputs.dir "${project.buildDir}/${project.jspCompile.classDir}"
                }
        )
        if (project.hasProperty('apiJar'))
            jspCompileTask.dependsOn('apiJar')
        jspCompileTask.dependsOn('jar')

        project.tasks.compileJspJava {
            dependsOn jspCompileTask
        }

        Task jspJar = project.task('jspJar',
                group: GroupNames.JSP,
                type: Jar,
                description: "produce jar file of jsps",
                { Jar jar ->
                    jar.classifier = CLASSIFIER
                    jar.from "${project.buildDir}/${project.jspCompile.classDir}"
                    jar.baseName = "${project.name}${BASE_NAME_EXTENSION}"
                    destinationDir = project.file(project.labkey.explodedModuleLibDir)
                }
        )

        jspJar.dependsOn(project.tasks.compileJspJava)

        project.artifacts {
            jspCompile jspJar
        }
        project.tasks.assemble.dependsOn(jspJar)
    }
}

