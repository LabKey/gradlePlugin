/*
 * Copyright (c) @@CURRENT_YEAR@@ LabKey Corporation
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

package org.labkey.@@MODULE_LOWERCASE_NAME@@;

public class @@MODULE_NAME@@Manager
{
    private static final @@MODULE_NAME@@Manager _instance = new @@MODULE_NAME@@Manager();

    private @@MODULE_NAME@@Manager()
    {
        // prevent external construction with a private default constructor
    }

    public static @@MODULE_NAME@@Manager get()
    {
        return _instance;
    }
}