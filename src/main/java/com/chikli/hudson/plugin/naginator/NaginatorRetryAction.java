package com.chikli.hudson.plugin.naginator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.ParametersAction;
import hudson.model.Run;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class NaginatorRetryAction implements Action {

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
            "symbol-arrow-redo-outline plugin-ionicons-api" : null;
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
        final List<Action> actions = new ArrayList<>();
        actions.add(action);
        actions.add(build.getAction(ParametersAction.class));
        actions.add(build.getAction(CauseAction.class));

        return build.getProject().scheduleBuild(delay, new NaginatorCause(build), actions.toArray(new Action[0]));
    }

}
