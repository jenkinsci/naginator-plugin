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

import com.google.common.collect.Sets;
import hudson.Functions;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.User;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.Permission;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;
import org.htmlunit.ElementNotFoundException;
import org.htmlunit.html.HtmlAnchor;
import org.jenkinsci.plugins.matrixauth.PermissionEntry;
import org.jenkinsci.plugins.matrixauth.inheritance.InheritParentStrategy;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.jenkinsci.plugins.matrixauth.AuthorizationType.EITHER;
import static org.junit.Assert.fail;

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
    
    private String getRetryLinkFor(AbstractBuild<?, ?> b) {
        return Functions.joinPath(r.contextPath, b.getUrl(), "retry") + "/";
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
        try (WebClient webClient = r.createWebClient()) {
            assertRetryLinkExists(b, webClient);
        }
    }
    
    private void assertRetryLinkNotExists(AbstractBuild<?, ?> b) throws Exception {
        try (WebClient webClient = r.createWebClient()) {
            assertRetryLinkNotExists(b, webClient);
        }
    }
    
    private void assertRetryLinkExists(AbstractBuild<?, ?> b, User u) throws Exception {
        try (WebClient webClient = r.createWebClient()) {
            assertRetryLinkExists(b, webClient.login(u.getId()));
        }
    }
    
    private void assertRetryLinkNotExists(AbstractBuild<?, ?> b, User u) throws Exception {
        try (WebClient webClient = r.createWebClient()) {
            assertRetryLinkNotExists(b, webClient.login(u.getId()));
        }
    }
    
    @Test
    public void testRetryLinkNotInSuccessBuild() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        FreeStyleBuild b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertRetryLinkNotExists(b);
    }
    
    @Test
    public void testRetryLinkInFailureBuild() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());
        FreeStyleBuild b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        assertRetryLinkExists(b);
    }
    
    @Test
    public void testRetryLinkInOptInProject() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new NaginatorOptOutProperty(false));
        p.getBuildersList().add(new FailureBuilder());
        FreeStyleBuild b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        assertRetryLinkExists(b);
    }
    
    @Test
    public void testRetryLinkNotInOptOutProject() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new NaginatorOptOutProperty(true));
        p.getBuildersList().add(new FailureBuilder());
        FreeStyleBuild b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        assertRetryLinkNotExists(b);
    }
    
    @Test
    public void testRetryLinkForPermittedUser() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy pmas = new ProjectMatrixAuthorizationStrategy();
        pmas.add(Jenkins.READ, new PermissionEntry(EITHER, "user1"));
        pmas.add(Item.READ, new PermissionEntry(EITHER, "user1"));
        pmas.add(Item.BUILD, new PermissionEntry(EITHER, "user1"));
        r.jenkins.setAuthorizationStrategy(pmas);
        
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());
        FreeStyleBuild b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        assertRetryLinkExists(b, User.getOrCreateByIdOrFullName("user1"));
    }
    
    
    @Test
    public void testRetryLinkNotForNonPermittedUser() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy pmas = new ProjectMatrixAuthorizationStrategy();
        pmas.add(Jenkins.READ, new PermissionEntry(EITHER, "user1"));
        pmas.add(Item.READ, new PermissionEntry(EITHER, "user1"));
        r.jenkins.setAuthorizationStrategy(pmas);
        
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());
        FreeStyleBuild b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        assertRetryLinkNotExists(b, User.getOrCreateByIdOrFullName("user1"));
    }
    
    @Test
    public void testRetryLinkForPermittedUserByProject() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy pmas = new ProjectMatrixAuthorizationStrategy();
        pmas.add(Jenkins.READ, new PermissionEntry(EITHER, "user1"));
        r.jenkins.setAuthorizationStrategy(pmas);
        
        FreeStyleProject p = r.createFreeStyleProject();
        
        Map<Permission, Set<PermissionEntry>> authMap = new HashMap<>();
        authMap.put(Item.READ, Sets.newHashSet(new PermissionEntry(EITHER, "user1")));
        authMap.put(Item.BUILD, Sets.newHashSet(new PermissionEntry(EITHER, "user1")));
        p.addProperty(new AuthorizationMatrixProperty(authMap, new InheritParentStrategy()));
        
        p.getBuildersList().add(new FailureBuilder());
        FreeStyleBuild b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        assertRetryLinkExists(b, User.getOrCreateByIdOrFullName("user1"));
    }
}
