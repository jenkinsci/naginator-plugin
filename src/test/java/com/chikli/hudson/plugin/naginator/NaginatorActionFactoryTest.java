/*
 * The MIT License
 * 
 * Copyright (c) 2015 IKEDA Yasuyuki
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

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import jenkins.model.Jenkins;
import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.model.User;
import hudson.security.Permission;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.ProjectMatrixAuthorizationStrategy;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.FailureBuilder;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.google.common.collect.Sets;

/**
 *
 */
public class NaginatorActionFactoryTest {
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();
    
    @After
    public void tearDown() {
        r.jenkins.setSecurityRealm(null);
        r.jenkins.setAuthorizationStrategy(null);
    }
    
    private String getRetryLinkFor(AbstractBuild<?, ?> b) throws Exception {
        return Functions.joinPath(r.contextPath, b.getUrl(), "retry");
    }
    
    private void assertRetryLinkExists(AbstractBuild<?, ?> b, WebClient wc) throws Exception {
        wc.getPage(b).getAnchorByHref(getRetryLinkFor(b));
    }
    
    private void assertRetryLinkNotExists(AbstractBuild<?, ?> b, WebClient wc) throws Exception {
        try {
            HtmlAnchor a = wc.getPage(b).getAnchorByHref(getRetryLinkFor(b));
            fail("Should not exist: " + a);
        } catch (ElementNotFoundException e) {
            // pass
        }
    }
    
    private void assertRetryLinkExists(AbstractBuild<?, ?> b) throws Exception {
        assertRetryLinkExists(b, r.createWebClient());
    }
    
    private void assertRetryLinkNotExists(AbstractBuild<?, ?> b) throws Exception {
        assertRetryLinkNotExists(b, r.createWebClient());
    }
    
    private void assertRetryLinkExists(AbstractBuild<?, ?> b, User u) throws Exception {
        assertRetryLinkExists(b, r.createWebClient().login(u.getId()));
    }
    
    private void assertRetryLinkNotExists(AbstractBuild<?, ?> b, User u) throws Exception {
        assertRetryLinkNotExists(b, r.createWebClient().login(u.getId()));
    }
    
    @Test
    public void testRetrylinkNotInSuccessBuild() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        FreeStyleBuild b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertRetryLinkNotExists(b);
    }
    
    @Test
    public void testRetrylinkInFailureBuild() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());
        FreeStyleBuild b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        assertRetryLinkExists(b);
    }
    
    @Test
    public void testRetrylinkInOptInProject() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new NaginatorOptOutProperty(false));
        p.getBuildersList().add(new FailureBuilder());
        FreeStyleBuild b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        assertRetryLinkExists(b);
    }
    
    @Test
    public void testRetrylinkNotInOptOutProject() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new NaginatorOptOutProperty(true));
        p.getBuildersList().add(new FailureBuilder());
        FreeStyleBuild b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        assertRetryLinkNotExists(b);
    }
    
    @Test
    public void testRetrylinkForPermittedUser() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy pmas = new ProjectMatrixAuthorizationStrategy();
        pmas.add(Jenkins.READ, "user1");
        pmas.add(Item.READ, "user1");
        pmas.add(Item.BUILD, "user1");
        r.jenkins.setAuthorizationStrategy(pmas);
        
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());
        FreeStyleBuild b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        assertRetryLinkExists(b, User.get("user1"));
    }
    
    
    @Test
    public void testRetrylinkNotForNonPermittedUser() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy pmas = new ProjectMatrixAuthorizationStrategy();
        pmas.add(Jenkins.READ, "user1");
        pmas.add(Item.READ, "user1");
        //pmas.add(Item.BUILD, "user1");
        r.jenkins.setAuthorizationStrategy(pmas);
        
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());
        FreeStyleBuild b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        assertRetryLinkNotExists(b, User.get("user1"));
    }
    
    @Test
    public void testRetrylinkForPermittedUserByProject() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy pmas = new ProjectMatrixAuthorizationStrategy();
        pmas.add(Jenkins.READ, "user1");
        r.jenkins.setAuthorizationStrategy(pmas);
        
        FreeStyleProject p = r.createFreeStyleProject();
        
        Map<Permission, Set<String>> authMap = new HashMap<Permission, Set<String>>();
        authMap.put(Item.READ, Sets.newHashSet("user1"));
        authMap.put(Item.BUILD, Sets.newHashSet("user1"));
        p.addProperty(new AuthorizationMatrixProperty(authMap));
        
        p.getBuildersList().add(new FailureBuilder());
        FreeStyleBuild b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        assertRetryLinkExists(b, User.get("user1"));
    }
}
