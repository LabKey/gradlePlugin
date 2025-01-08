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

package org.labkey.test.pages.@@MODULE_LOWERCASE_NAME@@;

import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.Locator;
import org.labkey.test.WebTestHelper;
import org.labkey.test.pages.LabKeyPage;
import org.openqa.selenium.WebElement;

/**
 * ~ Created from 'moduleTemplate' ~
 * This page class wraps the functionality of '@@MODULE_NAME@@Controller.BeginAction'
 */
public class @@MODULE_NAME@@BeginPage extends LabKeyPage<@@MODULE_NAME@@BeginPage.ElementCache>
{
    public @@MODULE_NAME@@BeginPage(WebDriverWrapper driver)
    {
        super(driver);
    }

    public static @@MODULE_NAME@@BeginPage beginAt(WebDriverWrapper driver)
    {
        return beginAt(driver, driver.getCurrentContainerPath());
    }

    public static @@MODULE_NAME@@BeginPage beginAt(WebDriverWrapper driver, String containerPath)
    {
        driver.beginAt(WebTestHelper.buildURL("@@MODULE_LOWERCASE_NAME@@", containerPath, "begin"));
        return new @@MODULE_NAME@@BeginPage(driver);
    }

    public String getHelloMessage()
    {
        return elementCache().helloMessage.getText();
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends LabKeyPage<ElementCache>.ElementCache
    {
        protected final WebElement helloMessage = Locator.tagWithName("div", "helloMessage").findWhenNeeded(this);
    }
}
