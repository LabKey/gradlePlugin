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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.labkey.gradle.plugin.XmlBeans

import javax.inject.Inject

/**
 * Task to compile XSD schema files into Java class files using the ant XMLBean
 */
@CacheableTask
abstract class SchemaCompile extends DefaultTask {

  @Inject abstract FileSystemOperations getFs()

  // This input declaration is used to defeat the gradle build cache when the xmlbeansVersion changes and should
  // remain even if there are no usages of this method. I don't know why the cache key doesn't change as a result of the
  // new jar file version, but it doesn't (perhaps an artifact of having to use the ant builder).
  @Input
  final abstract Property<String> xmlBeansVersion = project.objects.property(String).convention(project.hasProperty('xmlbeansVersion') ? (String) project.property('xmlbeansVersion') : null)

  @InputFiles
  @CompileClasspath
  abstract ConfigurableFileCollection getCompileClasspath()

  @InputDirectory
  @PathSensitive(PathSensitivity.RELATIVE)
  final abstract DirectoryProperty schemasDir = project.objects.directoryProperty().convention(project.layout.projectDirectory.dir(XmlBeans.SCHEMAS_DIR))

  // Marked as an OutputDirectory as Gradle wants everything to have such an annotation,
  // but we don't want these .java files picked up in the jar, so the jar task should be configured
  // to exclude .java files.
  @OutputDirectory
  final abstract DirectoryProperty srcGenDir = project.objects.directoryProperty().convention(project.layout.projectDirectory.dir("$project.labkey.srcGenDir/$XmlBeans.CLASS_DIR"))

  @OutputDirectory
  final abstract DirectoryProperty classesDir = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir(XmlBeans.CLASS_DIR).get())


  @TaskAction
  void cleanAndCompile() {
    // remove the directories containing the generated java files and the compiled classes when we have to make changes.
    fs.delete( {
      it.delete(srcGenDir.get())
      it.delete(classesDir.get())
    })
    ant.taskdef(
            name: 'xmlbean',
            classname: 'org.apache.xmlbeans.impl.tool.XMLBean',
            classpath: getCompileClasspath().getAsPath()
    )

    ant.xmlbean(
            schema: schemasDir.get(),
            srcgendir: srcGenDir.get(),
            classgendir: classesDir.get(),
            classpath: getCompileClasspath().getAsPath(),
            failonerror: true
    )
  }
}
