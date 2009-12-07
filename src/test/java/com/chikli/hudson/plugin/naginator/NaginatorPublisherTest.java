package com.chikli.hudson.plugin.naginator;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.Builder;

import java.io.IOException;
import java.io.Serializable;

import org.jvnet.hudson.test.HudsonTestCase;

import static org.junit.Assert.*;

public class NaginatorPublisherTest extends HudsonTestCase {
    
    private static final class MyBuilder extends Builder {
        private final String text;
        private final Result result;
        
        public MyBuilder(String text, Result result) {
            super();
            this.text = text;
            this.result = result;
        }
        
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                               BuildListener listener) throws InterruptedException,
                                                              IOException {
            listener.getLogger().println(text);
            build.setResult(result);
            return true;
        }
    }
    
    public void testSuccessNoRebuild() throws Exception {
        assertEquals(null, getDescription("build log", Result.SUCCESS,
                                          "foo", false, false));
    }

    public void testUnstableNoRebuild() throws Exception {
        assertEquals(null, getDescription("build log", Result.SUCCESS,
                                          "foo", false, false));
    }

    public void testUnstableWithRebuild() throws Exception {
        assertEquals("rebuild", getDescription("build log", Result.UNSTABLE,
                                               "foo", true, false));
    }

    public void testFailureWithRebuild() throws Exception {
        assertEquals("rebuild", getDescription("build log", Result.FAILURE,
                                               "foo", false, false));
    }

    public void testFailureWithUnstableRebuild() throws Exception {
        assertEquals("rebuild", getDescription("build log", Result.FAILURE,
                                               "foo", true, false));
    }

    public void testFailureWithoutRebuildRegexp() throws Exception {
        assertEquals(null, getDescription("build log", Result.FAILURE,
                                          "foo", false, true));
    }

    public void testFailureWithRebuildRegexp() throws Exception {
        assertEquals("rebuild", getDescription("build log foo", Result.FAILURE,
                                          "foo", false, true));
    }

    public void testUnstableWithoutRebuildRegexp() throws Exception {
        assertEquals(null, getDescription("build log", Result.UNSTABLE,
                                          "foo", true, true));
    }

    public void testUnstableWithRebuildRegexp() throws Exception {
        assertEquals("rebuild", getDescription("build log foo", Result.UNSTABLE,
                                               "foo", true, true));
    }

    	
    private String getDescription(String buildLog, Result result, String regexpForRerun,
                                  boolean rerunIfUnstable, boolean checkRegexp) throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MyBuilder(buildLog, result));
        NaginatorPublisher nag = new NaginatorPublisher(regexpForRerun, rerunIfUnstable, checkRegexp);
        nag.setDebug(true);
        project.getPublishersList().add(nag);
    
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        return build.getDescription();
    }

}
