package com.chikli.hudson.plugin.naginator.testutils;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.tasks.Builder;

import java.io.IOException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

public final class MyBuilder extends Builder {
    private final String text;
    private final Result result;
    private final int duration;

    public MyBuilder(String text, Result result) {
        super();
        this.text = text;
        this.result = result;
        this.duration = 0;
    }

    public MyBuilder(String text, Result result, int duration) {
        this.text = text;
        this.result = result;
        this.duration = duration;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                           BuildListener listener) throws InterruptedException,
                                                          IOException {
        if (duration > 0) Thread.sleep(duration);

        listener.getLogger().println(build.getEnvironment(listener).expand(text));
        build.setResult(result);
        return true;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<Builder> {
        public String getDisplayName() {
            return "MyBuilder";
        }
        public MyBuilder newInstance(StaplerRequest req, JSONObject data) {
            return new MyBuilder("foo", Result.SUCCESS);
        }
    }
}
