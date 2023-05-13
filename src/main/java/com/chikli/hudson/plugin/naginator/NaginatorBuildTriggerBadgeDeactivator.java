package com.chikli.hudson.plugin.naginator;

import hudson.Extension;
import hudson.model.Cause;
import org.jenkinsci.plugins.buildtriggerbadge.provider.BuildTriggerBadgeDeactivator;

@Extension
public class NaginatorBuildTriggerBadgeDeactivator extends BuildTriggerBadgeDeactivator {

    @Override
    public boolean vetoBadge(Cause cause) {
        return cause instanceof NaginatorCause;
    }
}
