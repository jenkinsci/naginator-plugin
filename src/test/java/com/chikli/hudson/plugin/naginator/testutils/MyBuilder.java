package com.chikli.hudson.plugin.naginator.testutils;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
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
    private final String successCondition;

    public MyBuilder(String text, Result result) {
        super();
        this.text = text;
        this.result = result;
        this.duration = 0;
        this.successCondition = null;
    }

    public MyBuilder(String text, Result result, int duration) {
        this.text = text;
        this.result = result;
        this.duration = duration;
        this.successCondition = null;
    }

    public MyBuilder(String text, String successCondition) {
        super();
        this.text = text;
        this.result = Result.SUCCESS;
        this.duration = 0;
        this.successCondition = successCondition;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                           BuildListener listener) throws InterruptedException,
                                                          IOException {
        if (duration > 0) Thread.sleep(duration);

        listener.getLogger().println(build.getEnvironment(listener).expand(text));
        build.setResult(result);
        if (successCondition != null) {
            Binding binding = new Binding(build.getEnvironment(listener));
            GroovyShell shell = new GroovyShell(binding);
            return (Boolean)shell.evaluate(successCondition);
        }
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
