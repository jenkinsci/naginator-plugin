package com.chikli.hudson.plugin.naginator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParametersAction;

import jenkins.model.Jenkins;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class NaginatorRetryAction implements Action {

    private boolean hasPermission() {
        Job<?, ?> job = Stapler.getCurrentRequest().findAncestorObject(Job.class);
        if (job != null) {
            return job.getACL().hasPermission(Item.BUILD);
        }
        
        Jenkins j = Jenkins.getInstance();
        return (j != null)?j.hasPermission(Item.BUILD):false;
    }

    public String getIconFileName() {
        return hasPermission() ?
            "refresh.png" : null;
    }

    public String getDisplayName() {
        return hasPermission() ?
            "Retry" : null;
    }

    public String getUrlName() {
        return hasPermission() ?
            "retry" : null;
    }

    public void doIndex(StaplerResponse res, @AncestorInPath AbstractBuild build) throws IOException {
        build.getACL().checkPermission(Item.BUILD);
        NaginatorRetryAction.scheduleBuild(build, 0, NaginatorListener.calculateRetryCount(build), 0);
        res.sendRedirect2(build.getUpUrl());
    }

    static boolean scheduleBuild(final AbstractBuild<?, ?> build, final int delay, int retryCount, int maxRetryCount) {
        return scheduleBuild(build, delay, new NaginatorAction(build, retryCount, maxRetryCount));
    }

    static boolean scheduleBuild(final AbstractBuild<?, ?> build, final int delay, final NaginatorAction action) {
        final List<Action> actions = new ArrayList<Action>();
        actions.add(action);
        actions.add(build.getAction(ParametersAction.class));
        actions.add(build.getAction(CauseAction.class));

        return build.getProject().scheduleBuild(delay, new NaginatorCause(build), actions.toArray(new Action[actions.size()]));
    }

}
