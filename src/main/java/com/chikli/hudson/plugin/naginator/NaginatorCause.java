package com.chikli.hudson.plugin.naginator;

import hudson.model.AbstractBuild;
import hudson.model.Cause;

/**
 * {@link Cause} for builds triggered by this plugin.
 * @author Alan.Harder@sun.com
 */
public class NaginatorCause extends Cause {

    private final String summary;
    private final String projectName;
    private final String sourceBuildUrl;
    private final int sourceBuildNumber;

    public NaginatorCause(AbstractBuild<?, ?> build) {
        this.summary = build.getDisplayName();
        this.projectName = build.getParent().getFullName();
        this.sourceBuildUrl = build.getUrl();
        this.sourceBuildNumber = build.getNumber();
    }

    @Override
    public String getShortDescription() {
        return Messages.NaginatorCause_Description(summary);
    }

    public String getSummary() { return this.summary; }

    public String getProjectName() { return this.projectName; }

    public String getSourceBuildUrl() { return this.sourceBuildUrl; }

    public int getSourceBuildNumber() { return this.sourceBuildNumber; }

}
