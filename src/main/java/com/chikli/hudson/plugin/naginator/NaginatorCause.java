package com.chikli.hudson.plugin.naginator;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.Run;

/**
 * {@link Cause} for builds triggered by this plugin.
 * @author Alan.Harder@sun.com
 */
public class NaginatorCause extends Cause {

    private final String summary;
    private final Integer sourceBuildNumber;
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
    public void onLoad(@NonNull AbstractBuild<?, ?> build) {
        this.project = build.getParent();
    }

    public String getSummary() { return this.summary; }

    public Job getProject() { return this.project; }

    public String getJobUrl() {
        return this.project.getUrl();
    }

    public Integer getSourceBuildNumber() { return this.sourceBuildNumber; }

    /**
     * @return the source build. <code>null</code> when the build is deleted.
     * @since 1.18
     */
    @Nullable
    public Run<?, ?> getSourceBuild() {
        if (getSourceBuildNumber() == null) {
            return null;
        }
        return getProject().getBuildByNumber(getSourceBuildNumber());
    }
}
