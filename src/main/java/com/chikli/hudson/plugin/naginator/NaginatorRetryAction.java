package com.chikli.hudson.plugin.naginator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.ParametersAction;

import jenkins.model.Jenkins;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class NaginatorRetryAction implements Action {

    public String getIconFileName() {
        return Jenkins.getInstance().hasPermission(Item.BUILD) ?
            "refresh.png" : null;
    }

    public String getDisplayName() {
        return Jenkins.getInstance().hasPermission(Item.BUILD) ?
            "Retry" : null;
    }

    public String getUrlName() {
        return Jenkins.getInstance().hasPermission(Item.BUILD) ?
            "retry" : null;
    }

    public void doIndex(StaplerResponse res, @AncestorInPath AbstractBuild build) throws IOException {
        Jenkins.getInstance().checkPermission(Item.BUILD);
        NaginatorRetryAction.scheduleBuild(build, 0);
        res.sendRedirect2(build.getUpUrl());
    }

    static boolean scheduleBuild(final AbstractBuild<?, ?> build, final int delay) {
        return scheduleBuild(build, delay, new NaginatorAction());
    }

    static boolean scheduleBuild(final AbstractBuild<?, ?> build, final int delay, final NaginatorAction action) {
        final List<Action> actions = new ArrayList<Action>();
        actions.add(action);
        actions.add(build.getAction(ParametersAction.class));
        actions.add(build.getAction(CauseAction.class));

        return build.getProject().scheduleBuild(delay, new NaginatorCause(build), actions.toArray(new Action[actions.size()]));
    }

}
