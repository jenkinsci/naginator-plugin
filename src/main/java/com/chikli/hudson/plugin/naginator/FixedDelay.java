package com.chikli.hudson.plugin.naginator;

import com.chikli.hudson.plugin.naginator.ScheduleDelay;
import hudson.Extension;
import hudson.model.AbstractBuild;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author: <a hef="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class FixedDelay extends ScheduleDelay {

    private int delay;

    @DataBoundConstructor
    public FixedDelay(int delay) {
        this.delay = delay;
    }

    public int getDelay() {
        return delay;
    }

    @Override
    public int computeScheduleDelay(AbstractBuild failedBuild) {
        return delay;
    }


    @Extension
    public static class DescriptorImpl extends ScheduleDelayDescriptor {
        @Override
        public String getDisplayName() {
            return "Fixed";
        }
    }
}
