package com.chikli.hudson.plugin.naginator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import hudson.model.Cause;
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

    @RequirePOST
    public void doIndex(StaplerResponse res, @CheckForNull @AncestorInPath AbstractBuild<?, ?> build) throws IOException {
        if (build == null) {
            // This should not happen as
            // this page is displayed only for AbstractBuild.
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        build.getParent().checkPermission(Item.BUILD);
        NaginatorRetryAction.scheduleBuild2(build, 0,
                new NaginatorAction(build, NaginatorListener.calculateRetryCount(build), 0),
                true);
        res.sendRedirect2(build.getUpUrl());
    }

    static boolean scheduleBuild(final AbstractBuild<?, ?> build, final int delay, int retryCount, int maxRetryCount) {
        return scheduleBuild(build, delay, new NaginatorAction(build, retryCount, maxRetryCount));
    }

    static boolean scheduleBuild(final AbstractBuild<?, ?> build, final int delay, final NaginatorAction action) {
        return scheduleBuild2(build, delay, action, false);
    }

    static boolean scheduleBuild2(final AbstractBuild<?, ?> build, final int delay, final NaginatorAction action, boolean replaceUser) {
        final List<Action> actions = new ArrayList<>();
        NaginatorCause cause = new NaginatorCause(build);
        actions.add(action);
        action.setCause(cause);
        actions.add(build.getAction(ParametersAction.class));
        actions.add(getCauseAction(build, replaceUser, cause));

        return build.getProject().scheduleBuild2(delay, actions.toArray(new Action[0])) != null;
    }

    private static CauseAction getCauseAction(AbstractBuild<?, ?> build, boolean replaceUser, NaginatorCause cause) {
        List<Cause> currentCauses = new ArrayList<>(build.getCauses());
        List<Cause> newCauses = currentCauses.stream().filter(c -> !((c instanceof Cause.UserIdCause) && replaceUser) && !(c instanceof NaginatorCause)).collect(Collectors.toList());
        newCauses.add(cause);
        if (replaceUser) {
            newCauses.add(new Cause.UserIdCause());
        }
        return new CauseAction(newCauses);
    }
}
