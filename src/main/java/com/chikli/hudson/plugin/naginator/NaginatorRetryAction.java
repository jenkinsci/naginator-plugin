package com.chikli.hudson.plugin.naginator;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.ParametersAction;
import hudson.model.Run;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;

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
        ParametersAction p = build.getAction(ParametersAction.class);
        build.getProject().scheduleBuild(0, new NaginatorCause(), p, new NaginatorAction());
        res.sendRedirect2(build.getUpUrl());
    }
}
