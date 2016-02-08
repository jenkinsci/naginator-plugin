package com.chikli.hudson.plugin.naginator;

import javax.annotation.CheckForNull;

import hudson.model.BuildBadgeAction;
import hudson.model.Run;

/**
 * @author: <a hef="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class NaginatorAction implements BuildBadgeAction {
    private final int retryCount;
    private final int maxRetryCount;
    private final Integer parentBuildNumber;

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
     * @deprecated use {@link #NaginatorAction(Run, int, int)} instead.
     */
    @Deprecated
    public NaginatorAction(int retryCount) {
        this(null, retryCount, 0);
    }

    /**
     * @param parentBuild the build to retry.
     * @param retryCount the number of retry this build is rescheduled for.
     * @param maxRetryCount the maximum number to retry. Can be 0 for indeterminable cases.
     * @since 1.17
     */
    public NaginatorAction(@CheckForNull Run<?, ?> parentBuild, int retryCount, int maxRetryCount) {
        this.parentBuildNumber = (parentBuild != null) ? parentBuild.getNumber() : null;
        this.retryCount = retryCount;
        this.maxRetryCount = maxRetryCount;
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

    /**
     * Returns the maximum number to reschedule.
     * This may be <code>0</code> for builds rescheduled with
     * older versions of naginator-plugin
     * for cases that the build is rescheduled manually,
     * or for cases the maximum number is indeterminable.
     * 
     * @return the maximum number to retry.
     * @since 1.17
     */
    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    /**
     * Returns the maximum number to reschedule.
     * This may be <code>null</code> for builds rescheduled with
     * older versions of naginator-plugin
     * 
     * @return the build number of the build to reschedule.
     * @since 1.17
     */
    @CheckForNull
    public Integer getParentBuildNumber() {
        return parentBuildNumber;
    }
}
