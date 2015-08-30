package com.chikli.hudson.plugin.naginator;

import hudson.matrix.Combination;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.*;
import hudson.model.listeners.RunListener;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:nicolas.deloof@cloudbees.com">Nicolas De loof</a>
 */
public class NaginatorListener extends RunListener<AbstractBuild<?,?>> {


    @Override
    public void onCompleted(AbstractBuild<?, ?> build, @Nonnull TaskListener listener) {
        // Do nothing for null or a single Matrix run. (Run only when all Matrix finishes)
        if (build == null || build instanceof MatrixRun) {
            return;
        }
        
        int retryCount = calculateRetryCount(build);
        
        List<NaginatorScheduleAction> actions = build.getActions(NaginatorScheduleAction.class);
        for (NaginatorScheduleAction action : actions) {
            if (action.shouldSchedule(build, listener, retryCount)) {
                int n = action.getDelay().computeScheduleDelay(build);
                LOGGER.log(Level.FINE, "about to try to schedule a build #{0} in {1} seconds for {2}",
                        new Object[]{build.getNumber(), n, build.getProject().getName()} );
                
                List<Combination> combsToRerun = new ArrayList<Combination>();

                if (action.isRerunMatrixPart()) {
                    if (build instanceof MatrixBuild) {
                        MatrixBuild mb = (MatrixBuild) build;
                        List<MatrixRun> matrixRuns = mb.getRuns();

                        for (MatrixRun r : matrixRuns) {
                            if (r.getNumber() == build.getNumber()) {
                                if (!action.shouldScheduleForMatrixRun(r, listener)) {
                                    continue;
                                }
                                
                                LOGGER.log(Level.FINE, "add combination to matrix rerun ({0})", r.getParent().getCombination().toString());
                                combsToRerun.add(r.getParent().getCombination());    
                            }
                        }

                    }
                }

                if (!combsToRerun.isEmpty()) {
                    LOGGER.log(Level.FINE, "schedule matrix rebuild");
                    scheduleMatrixBuild(build, combsToRerun, n);
                } else {
                    scheduleBuild(build, n);
                }
            }
        }
    }

    /**
     * @deprecated use {@link NaginatorScheduleAction#shouldSchedule(Run, TaskListener, int)}
     * to control scheduling.
     */
    @Deprecated
    public boolean canSchedule(Run build, NaginatorPublisher naginator) {
        Run r = build;
        int max = naginator.getMaxSchedule();
        if (max <=0) return true;
        int n = 0;

        while (r != null && r.getAction(NaginatorAction.class) != null) {
            if (n >= max) break;
            r = r.getPreviousBuild();
            n++;
        }

        return n < max;
    }

    private int calculateRetryCount(@Nonnull Run<?, ?> r) {
        int n = 0;
        
        while (r != null && r.getAction(NaginatorAction.class) != null) {
            r = r.getPreviousBuild();
            n++;
        }
        return n;
    }
    
    public boolean scheduleMatrixBuild(AbstractBuild<?, ?> build, List<Combination> combinations, int n) {
        NaginatorMatrixAction nma = new NaginatorMatrixAction();
        for (Combination c : combinations) {
            nma.addCombinationToRerun(c);
        }
        return NaginatorRetryAction.scheduleBuild(build, n, nma);
    }

    /**
     * Wrapper method for mocking purposes.
     */
    public boolean scheduleBuild(AbstractBuild<?, ?> build, int n) {
        return NaginatorRetryAction.scheduleBuild(build, n);
    }

    private static final Logger LOGGER = Logger.getLogger(NaginatorListener.class.getName());

}
