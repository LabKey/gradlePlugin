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
package org.labkey.gradle.plugin.extension

class JsDocExtension
{
    // TODO does this need to be here? Replace with Provider in JsDoc plugin?
    String root
    // TODO perhaps can configure differently to eliminate the extension.
    String[] paths = []
    // TODO does this need to be here? Replace with Provider in JsDoc plugin?
    File outputDir
}
