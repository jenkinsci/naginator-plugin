package com.chikli.hudson.plugin.naginator;

import hudson.model.AbstractBuild;
import hudson.model.Cause;

/**
 * {@link Cause} for builds triggered by this plugin.
 * @author Alan.Harder@sun.com
 */
public class NaginatorCause extends Cause {


    private final String summary;

    public NaginatorCause(AbstractBuild<?, ?> build) {
        this.summary = build.getDisplayName();
    }

    @Override
    public String getShortDescription() {
        return Messages.NaginatorCause_Description(summary);
    }
}
