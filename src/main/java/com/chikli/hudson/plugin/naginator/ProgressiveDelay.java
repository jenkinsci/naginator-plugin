package com.chikli.hudson.plugin.naginator;

import static java.lang.Math.min;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Run;

/**
 * @author: <a hef="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
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

        int n = 1;
        int delay = increment;
        Run r = failedBuild;
        while (r != null && r.getAction(NaginatorAction.class) != null) {
            r = r.getPreviousBuild();
            n++;
            delay += n * increment;
        }
        // delay = increment * n * (n + 1) / 2
        return max <= 0 ? delay : min(delay, max);
    }

    @Extension
    public static class DescriptorImpl extends ScheduleDelayDescriptor {
        @Override
        public String getDisplayName() {
            return "Progressive";
        }
    }
}
