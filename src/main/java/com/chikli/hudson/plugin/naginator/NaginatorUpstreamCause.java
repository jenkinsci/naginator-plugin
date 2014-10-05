package com.chikli.hudson.plugin.naginator;

import hudson.model.Cause.UpstreamCause;
import hudson.model.Run;

/**
 * {@link Cause} for builds triggered by this plugin.
 * @author nowell@strite.org
 */
public class NaginatorUpstreamCause extends UpstreamCause {
    public NaginatorUpstreamCause(Run<?, ?> up) {
        super(up);
    }

    @Override
    public String getShortDescription() {
        return Messages.NaginatorCause_Description();
    }
}