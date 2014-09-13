package com.chikli.hudson.plugin.naginator;

import hudson.model.Cause.UpstreamCause;
import hudson.model.Run;

/**
 * {@link Cause} for builds triggered by this plugin.
 * @author Alan.Harder@sun.com
 */
public class NaginatorCause extends UpstreamCause {
    public NaginatorCause(Run<?, ?> up) {
        super(up);
    }

    @Override
    public String getShortDescription() {
        return Messages.NaginatorCause_Description();
    }
}
