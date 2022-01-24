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
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.labkey.gradle.plugin.XmlBeans
import org.labkey.gradle.util.BuildUtils

/**
 * Task to compile XSD schema files into Java class files using the ant XMLBean
 */
@CacheableTask
class SchemaCompile extends DefaultTask {


  @InputDirectory
  @PathSensitive(PathSensitivity.RELATIVE)
  File getSchemasDir()
  {
    return project.file(XmlBeans.SCHEMAS_DIR)
  }

  // Marked as an OutputDirectory as Gradle 7 wants everything to have such an annotation,
  // but we don't want these .java files picked up in the jar, so the jar task should be configured
  // to exclude .java files.
  @OutputDirectory
  File getSrcGenDir()
  {
    return new File("$project.labkey.srcGenDir/$XmlBeans.CLASS_DIR")
  }

  @OutputDirectory
  File getClassesDir()
  {
    return new File("$project.buildDir/$XmlBeans.CLASS_DIR")
  }

  @TaskAction
  void compile() {
    ant.taskdef(
            name: 'xmlbean',
            classname: 'org.apache.xmlbeans.impl.tool.XMLBean',
            classpath: project.configurations.xmlbeans.asPath
    )
    // TODO get rid of this once we have updated to the later xmlbeans version please
    if (BuildUtils.compareVersions(project.property('xmlbeansVersion'), '5.0.0') == -1) {
      ant.xmlbean(
              javasource: "1.8",
              schema: getSchemasDir(),
              srcgendir: getSrcGenDir(),
              classgendir: getClassesDir(),
              classpath: project.configurations.xmlbeans.asPath,
              failonerror: true
      )
    }
    else {
      ant.xmlbean(
              schema: getSchemasDir(),
              srcgendir: getSrcGenDir(),
              classgendir: getClassesDir(),
              classpath: project.configurations.xmlbeans.asPath,
              failonerror: true
      )
    }
  }
}
