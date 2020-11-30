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

import org.labkey.gradle.plugin.Gwt

class GwtExtension
{
    String srcDir = Gwt.SOURCE_DIR
    String style = "OBF"
    String logLevel = "INFO"
    String extrasDir = "gwtExtras"
    Boolean draftCompile = false
    Boolean allBrowserCompile = true
}
