package com.chikli.hudson.plugin.naginator;

import org.jspecify.annotations.Nullable;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.Run;

/**
 * {@link Cause} for builds triggered by this plugin.
 * @author Alan.Harder@sun.com
 */
public class NaginatorCause extends Cause {

    private final @Nullable String summary;
    private final @Nullable Integer sourceBuildNumber;
    private transient @Nullable Job project;

    public NaginatorCause(AbstractBuild<?, ?> build) {
        this.summary = build.getDisplayName();
        this.sourceBuildNumber = build.getNumber();
    }

    @Override
    public String getShortDescription() {
        return summary == null
                ? Messages.NaginatorCause_DescriptionGeneral()
                : Messages.NaginatorCause_Description(summary);
    }

    @Override
    public void onAddedTo(AbstractBuild build) {
        this.project = build.getParent();
    }

    @Override
    public void onLoad(AbstractBuild<?, ?> build) {
        this.project = build.getParent();
    }

    public @Nullable String getSummary() { return this.summary; }

    public @Nullable Job getProject() { return this.project; }

    public @Nullable String getJobUrl() {
        return this.project == null ? null : this.project.getUrl();
    }

    public @Nullable Integer getSourceBuildNumber() { return this.sourceBuildNumber; }

    /**
     * @return the source build. <code>null</code> when the build is deleted.
     * @since 1.18
     */
    @Nullable
    public Run<?, ?> getSourceBuild() {
        Integer number = getSourceBuildNumber();
        if (number == null) {
            return null;
        }
        Job<?, ?> project = getProject();
        return project == null ? null : project.getBuildByNumber(number);
    }
}
