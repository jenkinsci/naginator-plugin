package com.chikli.hudson.plugin.naginator;

import com.chikli.hudson.plugin.naginator.ScheduleDelay;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.Run;
import org.kohsuke.stapler.DataBoundConstructor;

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

        // delay = the number of consective build problems * 5 mins
        // back off at most 3 hours

        int n=0;
        Run r = failedBuild;
        while (r != null && r.getAction(NaginatorAction.class) != null) {
            if (n >= max) break;
            r = r.getPreviousBuild();
            n++;
        }
        return n;
    }

    @Extension
    public static class DescriptorImpl extends ScheduleDelayDescriptor {
        @Override
        public String getDisplayName() {
            return "Progressively introduce delay until the next build";
        }
    }
}
