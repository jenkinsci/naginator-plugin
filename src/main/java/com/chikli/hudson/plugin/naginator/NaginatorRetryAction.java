package com.chikli.hudson.plugin.naginator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.CheckForNull;
import javax.servlet.http.HttpServletResponse;

import hudson.model.BuildBadgeAction;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.ParametersAction;
import hudson.model.Run;

import static java.util.Arrays.asList;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class NaginatorRetryAction implements Action {
    // While there is no common interfaces to mark Action as safe copyable, whitelist via class name
    private static final List<String> COPYABLE_ACTION_CLASSES = asList(
            "org.jenkinsci.plugins.github.pullrequest.GitHubPRBadgeAction",
            "com.github.kostyasha.github.integration.branch.GitHubBranchBadgeAction"
    );

    private boolean hasPermission() {
        Run<?, ?> run = Stapler.getCurrentRequest().findAncestorObject(Run.class);
        if (run == null) {
            // this page should be shown only when
            // there's a valid build in the path hierarchy.
            // (otherwise, we can't get what to retry)
            return false;
        }

        if (!(run instanceof AbstractBuild)) {
            // retry is applicable only to AbstractBuild
            // (Run such as WorkflowRun is not supported)
            return false;
        }

        return run.getParent().hasPermission(Item.BUILD);
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

    public void doIndex(StaplerResponse res, @CheckForNull @AncestorInPath AbstractBuild<?, ?> build) throws IOException {
        if (build == null) {
            // This should not happen as
            // this page is displayed only for AbstractBuild.
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        build.getParent().checkPermission(Item.BUILD);
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
        for (Action a : build.getActions(BuildBadgeAction.class)) {
            if (COPYABLE_ACTION_CLASSES.contains(a.getClass().getName())) {
                actions.add(a);
            }
        }

        return build.getProject().scheduleBuild(delay, new NaginatorCause(build), actions.toArray(new Action[actions.size()]));
    }

}
