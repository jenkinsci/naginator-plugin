package com.chikli.hudson.plugin.naginator.testutils;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;

public class TestSupport {
    private TestSupport() {
    }

    public static int lastBuildNumber(AbstractProject<?, ?> p) {
        if (p == null) {
            return -2;
        }
        AbstractBuild<?, ?> b = p.getLastBuild();
        if (b == null) {
            return -1;
        }
        return b.getNumber();
    }
}
