package com.chikli.hudson.plugin.naginator;

import hudson.model.Action;
import hudson.model.BuildBadgeAction;

/**
 * @author: <a hef="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class NaginatorAction implements BuildBadgeAction {
    private final int retryCount;

    /**
     * @deprecated use {@link NaginatorAction#NaginatorAction(int)}
     */
    @Deprecated
    public NaginatorAction() {
        this(0);
    }

    /**
     * @param retryCount the number of retry this build is rescheduled for.
     * @since 1.16
     */
    public NaginatorAction(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }

    /**
     * Returns the number of retry this build is rescheduled for.
     * This may be <code>0</code> for builds rescheduled with
     * older versions of naginator-plugin.
     * 
     * @return the number of retry this build is rescheduled for.
     * @since 1.16
     */
    public int getRetryCount() {
        return retryCount;
    }
}
