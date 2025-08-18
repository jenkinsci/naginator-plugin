package com.chikli.hudson.plugin.naginator;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractBuild;
import org.kohsuke.stapler.DataBoundConstructor;
import static java.lang.Math.min;

/**
 * Need to make a fork.
 * Expontential delay
 * @author <a href="mailto:daniel.alfonsetti@gmail.com">Daniel Alfonsetti</a>
 */
public class ExponentialDelay extends ScheduleDelay {

    private int backoffBase;
    private int max;

    @DataBoundConstructor
    public ExponentialDelay(int backoff_base, int max) {
        this.backoffBase = backoff_base;
        this.max = max;
    }

    public int getBackoffBase() {
        return backoffBase;
    }

    public int getMax() {
        return max;
    }

    // number of seconds.
    @Override
    public int computeScheduleDelay(AbstractBuild failedBuild) {
        // int n = getRetryCount(failedBuild);
        // // int delay = (int) Math.pow(this.backoffBase, n);
        // int delay = 50+n;
        // return max <= 0 ? delay : min(delay, max);
        return 1;
    }

    private int getRetryCount(AbstractBuild<?, ?> failedBuild) {
        NaginatorAction action = failedBuild.getAction(NaginatorAction.class);
        if (action == null) {
            return 0;
        }
        return action.getRetryCount();
    }

    @Extension
    public static class DescriptorImpl extends ScheduleDelayDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Exponential";
        }
    }
}
