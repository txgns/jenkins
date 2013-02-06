/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.model;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import hudson.model.UpdateSite.Data;
import hudson.util.FormValidation;
import hudson.util.PersistedList;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import org.apache.commons.io.IOUtils;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class UpdateSiteTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test public void updateDirectlyWithJson() throws Exception {
        UpdateSite us = new UpdateSite("default", UpdateSiteTest.class.getResource("update-center.json").toExternalForm());
        assertNull(us.getPlugin("AdaptivePlugin"));
        FormValidation result = us.updateDirectly().get();
        assertTrue(result.equals(FormValidation.ok()));
        assertNotNull(us.getPlugin("AdaptivePlugin"));
    }
    
    @Test public void updateDirectlyWithHtml() throws Exception {
        UpdateSite us = new UpdateSite("default", UpdateSiteTest.class.getResource("update-center.json").toExternalForm());
        assertNull(us.getPlugin("AdaptivePlugin"));
        FormValidation result = us.updateDirectly().get();
        assertTrue(result.equals(FormValidation.ok()));
        assertNotNull(us.getPlugin("AdaptivePlugin"));
    }
}
