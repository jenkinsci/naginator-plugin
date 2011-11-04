package com.chikli.hudson.plugin.naginator;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapper.Environment;
import hudson.tasks.Builder;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.jvnet.hudson.test.HudsonTestCase;

public class NaginatorListenerTest extends HudsonTestCase {
    
    private final static class MyBuilder extends Builder {
        private final String text;
        private final Result result;
        private final int duration;

        public MyBuilder(String text, Result result) {
            super();
            this.text = text;
            this.result = result;
            this.duration = 0;
        }

        private MyBuilder(String text, Result result, int duration) {
            this.text = text;
            this.result = result;
            this.duration = duration;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                               BuildListener listener) throws InterruptedException,
                                                              IOException {
            if (duration > 0) Thread.sleep(duration);

            listener.getLogger().println(text);
            build.setResult(result);
            return true;
        }
    }

    public void testSuccessNoRebuild() throws Exception {
        assertEquals(false, isScheduledForRetry("build log", Result.SUCCESS, "foo", false, false));
    }

    public void testUnstableNoRebuild() throws Exception {
        assertEquals(false, isScheduledForRetry("build log", Result.SUCCESS, "foo", false, false));
    }

    public void testUnstableWithRebuild() throws Exception {
        assertEquals(true, isScheduledForRetry("build log", Result.UNSTABLE, "foo", true, false));
    }

    public void testFailureWithRebuild() throws Exception {
        assertEquals(true, isScheduledForRetry("build log", Result.FAILURE, "foo", false, false));
    }

    public void testFailureWithUnstableRebuild() throws Exception {
        assertEquals(true, isScheduledForRetry("build log", Result.FAILURE, "foo", true, false));
    }

    public void testFailureWithoutRebuildRegexp() throws Exception {
        assertEquals(false, isScheduledForRetry("build log", Result.FAILURE, "foo", false, true));
    }

    public void testFailureWithRebuildRegexp() throws Exception {
        assertEquals(true, isScheduledForRetry("build log foo", Result.FAILURE, "foo", false, true));
    }

    public void testUnstableWithoutRebuildRegexp() throws Exception {
        assertEquals(false, isScheduledForRetry("build log", Result.UNSTABLE, "foo", true, true));
    }

    public void testUnstableWithRebuildRegexp() throws Exception {
        assertEquals(true, isScheduledForRetry("build log foo", Result.UNSTABLE, "foo", true, true));
    }

    public void testWithBuildWrapper() throws Exception {

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MyBuilder("foo", Result.SUCCESS, 1000));
        NaginatorPublisher nag = new NaginatorPublisher("foo", false, false);
        project.getPublishersList().add(nag);
        BuildWrapper failTheBuild = new FailTheBuild();
        project.getBuildWrappersList().add(failTheBuild);

        assertEquals(true, isScheduledForRetry(project));
    }


    private boolean isScheduledForRetry(String buildLog, Result result, String regexpForRerun,
                                    boolean rerunIfUnstable, boolean checkRegexp) throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MyBuilder(buildLog, result));
        NaginatorPublisher nag = new NaginatorPublisher(regexpForRerun, rerunIfUnstable, checkRegexp);
        project.getPublishersList().add(nag);

        return isScheduledForRetry(project);
    }

    private boolean isScheduledForRetry(FreeStyleProject project) throws InterruptedException, ExecutionException {
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        Queue queue = Hudson.getInstance().getQueue();
        Queue.Item[] tasks = queue.getItems();
        boolean scheduled = tasks.length > 0;
        queue.clear();
        return scheduled;
    }

    private static final class FailTheBuild extends BuildWrapper {
        @Override
        public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            return new Environment() {
                @Override
                public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                    build.setResult(Result.FAILURE);
                    return true;
                }
            };
        }
    }
}
