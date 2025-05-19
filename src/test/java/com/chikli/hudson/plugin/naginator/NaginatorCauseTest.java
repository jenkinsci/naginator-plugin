/*
 * The MIT License
 *
 * Copyright (c) 2019 IKEDA Yasuyuki
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

package com.chikli.hudson.plugin.naginator;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static com.chikli.hudson.plugin.naginator.testutils.TestSupport.lastBuildNumber;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link NaginatorCause}
 */
@WithJenkins
class NaginatorCauseTest {

    private static JenkinsRule j;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * @return the expected value of "rootURL" in jelly.
     */
    public String getRootURL() {
        return j.contextPath + "/";
    }

    @Test
    void testCauseLink() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());
        p.getPublishersList().add(new NaginatorPublisher(
            "", // regexForRerun
            true,       // rerunIfUnstable
            false,      // rerunMatrixPart
            false,      // checkRegexp
            2,          // maxSchedule
            new FixedDelay(0)   // delay
        ));
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();

        assertEquals(3, lastBuildNumber(p));

        try (WebClient wc = j.createWebClient()) {
            {
                FreeStyleBuild b = p.getBuildByNumber(3);
                FreeStyleBuild causeBuild = p.getBuildByNumber(2);
                HtmlPage page = wc.getPage(b);
                HtmlAnchor anchor = page.getFirstByXPath("//a[contains(@class,'naginator-cause')]");
                assertNotNull(anchor);
                assertEquals(
                        getRootURL() + causeBuild.getUrl(),
                        anchor.getHrefAttribute()
                );
            }
            {
                FreeStyleBuild b = p.getBuildByNumber(2);
                FreeStyleBuild causeBuild = p.getBuildByNumber(1);
                HtmlPage page = wc.getPage(b);
                HtmlAnchor anchor = page.getFirstByXPath("//a[contains(@class,'naginator-cause')]");
                assertNotNull(anchor);
                assertEquals(
                        getRootURL() + causeBuild.getUrl(),
                        anchor.getHrefAttribute()
                );
            }
        }
    }

    @Test
    void testDisabledCauseLink() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());
        p.getPublishersList().add(new NaginatorPublisher(
            "", // regexForRerun
            true,       // rerunIfUnstable
            false,      // rerunMatrixPart
            false,      // checkRegexp
            2,          // maxSchedule
            new FixedDelay(0)   // delay
        ));
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();

        assertEquals(3, lastBuildNumber(p));

        p.getBuildByNumber(1).delete();

        try (WebClient wc = j.createWebClient()) {
            {
                FreeStyleBuild b = p.getBuildByNumber(3);
                FreeStyleBuild causeBuild = p.getBuildByNumber(2);
                HtmlPage page = wc.getPage(b);
                HtmlAnchor anchor = page.getFirstByXPath("//a[contains(@class,'naginator-cause')]");
                assertNotNull(anchor);
                assertEquals(
                        getRootURL() + causeBuild.getUrl(),
                        anchor.getHrefAttribute()
                );
            }
            {
                FreeStyleBuild b = p.getBuildByNumber(2);
                HtmlPage page = wc.getPage(b);
                HtmlAnchor anchor = page.getFirstByXPath("//a[contains(@class,'naginator-cause')]");
                assertNull(anchor);
            }
        }
    }

    @Issue("JENKINS-50751")
    @Test
    void testCauseLinkWithLargeNumber() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());
        p.getPublishersList().add(new NaginatorPublisher(
            "", // regexForRerun
            true,       // rerunIfUnstable
            false,      // rerunMatrixPart
            false,      // checkRegexp
            2,          // maxSchedule
            new FixedDelay(0)   // delay
        ));
        p.updateNextBuildNumber(2000);
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();

        assertEquals(2002, lastBuildNumber(p));

        try (WebClient wc = j.createWebClient()) {
            {
                FreeStyleBuild b = p.getBuildByNumber(2002);
                FreeStyleBuild causeBuild = p.getBuildByNumber(2001);
                HtmlPage page = wc.getPage(b);
                HtmlAnchor anchor = page.getFirstByXPath("//a[contains(@class,'naginator-cause')]");
                assertNotNull(anchor);
                assertEquals(
                        getRootURL() + causeBuild.getUrl(),
                        anchor.getHrefAttribute()
                );
            }
            {
                FreeStyleBuild b = p.getBuildByNumber(2001);
                FreeStyleBuild causeBuild = p.getBuildByNumber(2000);
                HtmlPage page = wc.getPage(b);
                HtmlAnchor anchor = page.getFirstByXPath("//a[contains(@class,'naginator-cause')]");
                assertNotNull(anchor);
                assertEquals(
                        getRootURL() + causeBuild.getUrl(),
                        anchor.getHrefAttribute()
                );
            }
        }
    }

    @Issue("SECURITY-2946")
    @Test
    void testEscapedDisplayName() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild build1 = p.scheduleBuild2(0).get();
        build1.setDisplayName("<div id=\"unescaped-displayname\">bad displayname</div>");
        FreeStyleBuild build2 = p.scheduleBuild2(0, new NaginatorCause(build1)).get();

        try (WebClient wc = j.createWebClient()) {
            HtmlPage page = wc.getPage(build2);
            DomElement unescaped = page.getElementById("unescaped-displayname");
            assertNull(unescaped);
        }
    }
}
