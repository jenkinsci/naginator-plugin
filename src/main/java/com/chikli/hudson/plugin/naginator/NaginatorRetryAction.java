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
        return "refresh.png";
    }

    public String getDisplayName() {
        return "Retry";
    }

    public String getUrlName() {
        return "retry";
    }

    public void doIndex(StaplerResponse res, @AncestorInPath AbstractBuild build) throws IOException {
        Jenkins.getInstance().checkPermission(Item.BUILD);
        ParametersAction p = build.getAction(ParametersAction.class);
        build.getProject().scheduleBuild(0, new NaginatorUpstreamCause((Run) build), p, new NaginatorAction());
        res.sendRedirect2(build.getUpUrl());
    }
}
