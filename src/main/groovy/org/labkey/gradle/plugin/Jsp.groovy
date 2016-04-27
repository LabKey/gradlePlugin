package org.labkey.gradle.plugin

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.bundling.Jar
import org.labkey.gradle.task.JspCompile2Java

/**
 * Created by susanh on 4/11/16.
 */
class Jsp extends LabKey
{
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
                force 'javax.servlet:servlet-api:3.1' // the version number here is sort of irrelevant until we use a repository other than the file system; it just needs to be something other than the old 2.4 veresion in external/libs/build
            }
        }
    }

    private void addDependencies(Project project)
    {
        project.dependencies
                {
                    jspCompile  'org.apache.tomcat:jasper',
                        'org.apache.tomcat:jsp-api',
                        'javax.servlet:servlet-api:3.1',
                        'org.apache.tomcat:tomcat-juli',
                        'org.apache.tomcat:tomcat-api',
                        'org.apache.tomcat:tomcat-util-scan',
                        'org.apache.tomcat:tomcat-util',
                        'org.apache.tomcat:el-api',
                        'org.apache:jasper-el',
                        project.project(":server:api"),
                        "org.labkey:${project.name}-api"
                    jsp     'org.apache.tomcat:jasper',
                            'org.apache.tomcat:bootstrap',
                            'org.apache.tomcat:tomcat-juli'
                }
    }

    private void addJspTasks(Project project)
    {
        def FileTree jspTree = project.fileTree("src").include('**/*.jsp');
        jspTree += project.fileTree("resources").include("**/*.jsp");

        def Task jspCompileTask = project.task('jsp2Java',
                group: "jsp",
                type: JspCompile2Java,
                description: "compile jsp files into Java classes",
                {
                    inputs.files jspTree
                    outputs.dir "${project.buildDir}/${project.jspCompile.classDir}"
                }
        )

        project.tasks.compileJspJava {
            dependsOn jspCompileTask
        }

        def Task jspJar = project.task('jspJar',
                group: "jsp",
                type: Jar,
                description: "produce jar file of jsps", {
            from "${project.buildDir}/${project.jspCompile.classDir}"
            exclude '**/*.java'
            //baseName "${project.name}_jsp"
            archiveName "${project.name}_jsp.jar" // TODO remove this in favor of a versioned jar file when other items have change
            destinationDir = project.file(project.labkey.libDir)
        })
        jspJar.dependsOn(project.tasks.compileJspJava)

        project.artifacts {
            jspCompile jspJar
        }
        project.tasks.assemble.dependsOn(jspJar)
    }
}

class JspCompileExtension
{
    def String tempDir = "jspTempDir"
    def String classDir = "$tempDir/classes"
}
