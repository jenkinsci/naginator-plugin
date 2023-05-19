package com.chikli.hudson.plugin.naginator;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class NaginatorOptOutProperty extends JobProperty<AbstractProject<?, ?>> {

    private boolean optOut;

    @DataBoundConstructor
    public NaginatorOptOutProperty(boolean optOut) {
        this.optOut = optOut;
    }

    public boolean isOptOut() {
        return optOut;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Naginator Property";
        }
    }
}
