package com.chikli.hudson.plugin.naginator;

import static java.lang.Math.min;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Run;

/**
 * @author: <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ProgressiveDelay extends ScheduleDelay {

    private int increment;

    private int max;

    @DataBoundConstructor
    public ProgressiveDelay(int increment, int max) {
        this.increment = increment;
        this.max = max;
    }

    public int getIncrement() {
        return increment;
    }

    public int getMax() {
        return max;
    }

    @Override
    public int computeScheduleDelay(AbstractBuild failedBuild) {

        // if a build fails for a reason that cannot be immediately fixed,
        // immediate rescheduling may cause a very tight loop.
        // combined with publishers like e-mail, IM, this could flood the users.
        //
        // so to avoid this problem, progressively introduce delay until the next build

        int n = getRetryCount(failedBuild);
        int factor = (n + 1) * (n + 2) / 2;
        int delay = increment * factor;
        return max <= 0 ? delay : min(delay, max);
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
        @Override
        public String getDisplayName() {
            return "Progressive";
        }
    }
}
