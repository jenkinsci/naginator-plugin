package com.chikli.hudson.plugin.naginator;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Job;

import javax.annotation.Nonnull;

/**
 * {@link Cause} for builds triggered by this plugin.
 * @author Alan.Harder@sun.com
 */
public class NaginatorCause extends Cause {

    private final String summary;
    private final int sourceBuildNumber;
    private transient Job project;

    public NaginatorCause(AbstractBuild<?, ?> build) {
        this.summary = build.getDisplayName();
        this.sourceBuildNumber = build.getNumber();
    }

    @Override
    public String getShortDescription() {
        return Messages.NaginatorCause_Description(summary);
    }

    @Override
    public void onAddedTo(AbstractBuild build) {
        this.project = build.getParent();
    }

    @Override
    public void onLoad(@Nonnull AbstractBuild<?,?> build) {
        this.project = build.getParent();
    }

    public String getSummary() { return this.summary; }

    public Job getProject() { return this.project; }

    public String getJobUrl() {
        return this.project.getUrl();
    }

    public int getSourceBuildNumber() { return this.sourceBuildNumber; }

}
