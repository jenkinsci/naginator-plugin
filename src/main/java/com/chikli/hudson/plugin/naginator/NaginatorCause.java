package com.chikli.hudson.plugin.naginator;

import hudson.model.Cause;

/**
 * {@link Cause} for builds triggered by this plugin.
 * @author Alan.Harder@sun.com
 * Deprecated in favor of NaginatorUpstreamCause but leaving in place to ensure
 * that existing installations that reference this Cause in their build history
 * do not run into issues when displaying Causes prior to upgrade to newer
 * versions.
 */
public class NaginatorCause extends Cause {

    @Override
    public String getShortDescription() {
        return Messages.NaginatorCause_Description();
    }
}
