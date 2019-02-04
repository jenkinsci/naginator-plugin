package com.chikli.hudson.plugin.naginator;

import hudson.DescriptorExtensionList;
import hudson.ExtensionList;
import hudson.model.*;
import jenkins.model.Jenkins;

/**
 * Defines schedules policy to trigger a new build after failure
 * @author: <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class ScheduleDelay extends AbstractDescribableImpl<ScheduleDelay> {

    public abstract int computeScheduleDelay(AbstractBuild failedBuild);

    public static DescriptorExtensionList<ScheduleDelay, Descriptor<ScheduleDelay>> all() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return null;
        }
        return j.getDescriptorList(ScheduleDelay.class);
    }

    public static abstract class ScheduleDelayDescriptor extends Descriptor<ScheduleDelay> {
    }
}
