package com.chikli.hudson.plugin.naginator;

import hudson.model.Cause;

/**
 * {@link Cause} for builds triggered by this plugin.
 * @author Alan.Harder@sun.com
 */
public class NaginatorCause extends Cause {

    @Override
    public String getShortDescription() {
        return Messages.NaginatorCause_Description();
    }
}
