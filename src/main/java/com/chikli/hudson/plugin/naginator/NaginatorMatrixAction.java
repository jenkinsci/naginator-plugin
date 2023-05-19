package com.chikli.hudson.plugin.naginator;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.matrix.Combination;
import hudson.model.Run;

import java.util.ArrayList;
import java.util.List;

/**
 * This is an extention for the NaginatorAction class which used to store the
 * combinations to rerun.
 * @author galunto
 */
public class NaginatorMatrixAction extends NaginatorAction {
    private List<Combination> combsToRerun;

    /**
     * @deprecated use {@link NaginatorMatrixAction#NaginatorMatrixAction(int)}.
     */
    @Deprecated
    public NaginatorMatrixAction() {
        this(0);
    }

    /**
     * @param retryCount the number of retry this build is rescheduled for.
     * @since 1.16
     * @deprecated use {@link #NaginatorMatrixAction(Run, int, int)} instead.
     */
    @Deprecated
    public NaginatorMatrixAction(int retryCount) {
        this(null, retryCount, 0);
    }
    
    /**
     * @param parentBuild the build to be rescheduled.
     * @param retryCount the number of retry this build is rescheduled for.
     * @param maxRetryCount the maximum number to retry. Can be 0 for indeterminable cases.
     * @since 1.17
     */
    public NaginatorMatrixAction(@CheckForNull Run<?, ?> parentBuild, int retryCount, int maxRetryCount) {
        super(parentBuild, retryCount, maxRetryCount);
        this.combsToRerun = new ArrayList<Combination>();
    }
    
    public void addCombinationToRerun(Combination combination) {
        this.combsToRerun.add(combination);
    }

    public List<Combination> getCombinationsToRerun() {
        return this.combsToRerun;
    }
    
    public boolean isCombinationNeedsRerun(Combination combination) {
        return this.combsToRerun.contains(combination);
    }
}
