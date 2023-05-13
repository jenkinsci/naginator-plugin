package com.chikli.hudson.plugin.naginator;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.Combination;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.*;
import hudson.model.Run.RunnerAbortedException;
import hudson.model.listeners.RunListener;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:nicolas.deloof@cloudbees.com">Nicolas De loof</a>
 */
@Extension
public class NaginatorListener extends RunListener<AbstractBuild<?,?>> {


    @Override
    public void onCompleted(AbstractBuild<?, ?> build, @NonNull TaskListener listener) {
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
                    scheduleMatrixBuild(build, combsToRerun, n, retryCount + 1, action.getMaxSchedule());
                } else if (build instanceof MatrixBuild && action.isRerunMatrixPart()) {
                    // No children to rerun
                    switch (action.getNoChildStrategy()) {
                    case RerunWhole:
                        scheduleBuild(build, n, retryCount + 1, action.getMaxSchedule());
                        break;
                    case RerunEmpty:
                        LOGGER.log(Level.FINE, "schedule matrix rebuild");
                        scheduleMatrixBuild(build, combsToRerun, n, retryCount + 1, action.getMaxSchedule());
                        break;
                    case DontRun:
                        continue;   // confusing, but back to the look for NaginatorScheduleAction
                    }
                } else {
                    scheduleBuild(build, n, retryCount + 1, action.getMaxSchedule());
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Environment setUpEnvironment(@SuppressWarnings("rawtypes") AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException, RunnerAbortedException
    {
        AbstractBuild<?, ?> rootBuild = build.getRootBuild();
        if (rootBuild == null) {
            // getRootBuild() should not be null, 
            // but some builds irregularly returns null. 
            rootBuild = build;
        }
        final NaginatorAction action = rootBuild.getAction(NaginatorAction.class);
        if (action == null) {
            return null;
        }
        
        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                env.put("NAGINATOR_COUNT", Integer.toString(action.getRetryCount()));
                env.put("NAGINATOR_MAXCOUNT", Integer.toString(action.getMaxRetryCount()));
                Integer parentBuildNumber = action.getParentBuildNumber();
                if (parentBuildNumber != null) {
                    env.put("NAGINATOR_BUILD_NUMBER", parentBuildNumber.toString());
                }
            }
        };
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

    public static int calculateRetryCount(@NonNull Run<?, ?> r) {
        NaginatorAction naginatorAction = r.getAction(NaginatorAction.class);
        if (naginatorAction == null) {
            return 0;
        }
        if (naginatorAction.getRetryCount() > 0) {
            return naginatorAction.getRetryCount();
        }
        
        // fallback for build made by older versions.
        int n = 0;
        
        while (r != null && r.getAction(NaginatorAction.class) != null) {
            r = r.getPreviousBuild();
            n++;
        }
        return n;
    }
    
    /**
     * @deprecated use {@link NaginatorScheduleAction} to make a build rescheduled.
     */
    @Deprecated
    public boolean scheduleMatrixBuild(AbstractBuild<?, ?> build, List<Combination> combinations, int n) {
        return scheduleMatrixBuild(build, combinations, n, NaginatorListener.calculateRetryCount(build), 0);
    }
    
    private boolean scheduleMatrixBuild(AbstractBuild<?, ?> build, List<Combination> combinations, int delay, int retryCount, int maxRetryCount) {
        NaginatorMatrixAction nma = new NaginatorMatrixAction(build, retryCount, maxRetryCount);
        for (Combination c : combinations) {
            nma.addCombinationToRerun(c);
        }
        return NaginatorRetryAction.scheduleBuild(build, delay, nma);
    }

    /**
     * Wrapper method for mocking purposes.
     * 
     * @deprecated use {@link NaginatorScheduleAction} to make a build rescheduled.
     */
    @Deprecated
    public boolean scheduleBuild(AbstractBuild<?, ?> build, int n) {
        return scheduleBuild(build, n, NaginatorListener.calculateRetryCount(build), 0);
    }

    private boolean scheduleBuild(AbstractBuild<?, ?> build, int n, int retryCount, int maxRetryCount) {
        return NaginatorRetryAction.scheduleBuild(build, n, retryCount, maxRetryCount);
    }

    private static final Logger LOGGER = Logger.getLogger(NaginatorListener.class.getName());

}
