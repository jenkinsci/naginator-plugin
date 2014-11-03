package com.chikli.hudson.plugin.naginator;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.ParametersAction;
import hudson.model.Run;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

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
        ParametersAction p = build.getAction(ParametersAction.class);
        build.getProject().scheduleBuild(0, new NaginatorCause(), p, new NaginatorAction());
        res.sendRedirect2(build.getUpUrl());
    }
}
