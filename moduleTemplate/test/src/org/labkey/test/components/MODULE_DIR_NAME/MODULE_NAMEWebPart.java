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

package org.labkey.test.components.@@MODULE_LOWERCASE_NAME@@;

import org.labkey.test.Locator;
import org.labkey.test.components.BodyWebPart;
import org.labkey.test.components.html.Input;
import org.labkey.test.pages.LabKeyPage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import static org.labkey.test.components.html.Input.Input;

/**
 * ~ Created from 'moduleTemplate' ~
 * Example component for a hypothetical webpart containing an input and a save button
 * TODO: Remove if unneeded
 */
public class @@MODULE_NAME@@WebPart extends BodyWebPart<@@MODULE_NAME@@WebPart.ElementCache>
{
    public @@MODULE_NAME@@WebPart(WebDriver driver)
    {
        this(driver, 0);
    }

    public @@MODULE_NAME@@WebPart(WebDriver driver, int index)
    {
        super(driver, "@@MODULE_NAME@@", index);
    }

    public @@MODULE_NAME@@WebPart setInput(String value)
    {
        elementCache().input.set(value);
        // TODO: Methods that don't navigate should return this object
        return this;
    }

    public LabKeyPage<?> clickSave()
    {
        getWrapper().clickAndWait(elementCache().button);
        // TODO: Methods that navigate should return an appropriate page object
        return new LabKeyPage<>(getDriver());
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends BodyWebPart<ElementCache>.ElementCache
    {
        protected final WebElement button = Locator.tag("button").withText("Save").findWhenNeeded(this);
        protected final Input input = Input(Locator.tag("input"), getDriver()).findWhenNeeded(this);
    }
}