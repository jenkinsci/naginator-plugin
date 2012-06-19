package com.chikli.hudson.plugin.naginator;

import hudson.model.*;
import hudson.model.listeners.RunListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static hudson.model.Result.SUCCESS;

/**
 * @author <a href="mailto:nicolas.deloof@cloudbees.com">Nicolas De loof</a>
 */
public class NaginatorListener extends RunListener<AbstractBuild<?,?>> {


    @Override
    public void onCompleted(AbstractBuild<?, ?> build, TaskListener listener) {
        if (build.getResult() == SUCCESS) {
            return;
        }

        NaginatorPublisher naginator = build.getProject().getPublishersList().get(NaginatorPublisher.class);

        // JENKINS-13791
        if (naginator == null) {
            return;
        }

        // If we're not set to rerun if unstable, and the build's unstable, return true.
        if ((!naginator.isRerunIfUnstable()) && (build.getResult() == Result.UNSTABLE)) {
            return;
        }

        // If we're supposed to check for a regular expression in the build output before
        // scheduling a new build, do so.
        if (naginator.isCheckRegexp()) {
            LOGGER.log(Level.FINEST, "Got checkRegexp == true");

            String regexpForRerun = naginator.getRegexpForRerun();
            if ((regexpForRerun !=null) && (!regexpForRerun.equals(""))) {
                LOGGER.log(Level.FINEST, "regexpForRerun - " + regexpForRerun);

                try {
                    // If parseLog returns false, we didn't find the regular expression,
                    // so return true.
                    if (!parseLog(build.getLogFile(), regexpForRerun)) {
                        LOGGER.log(Level.FINEST, "regexp not in logfile");
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace(listener
                                      .error("error while parsing logs for naginator - forcing rebuild."));
                }
            }
        }

        if (canSchedule(build, naginator)) {
            int n = naginator.getDelay().computeScheduleDelay(build);
            LOGGER.log(Level.FINE, "about to try to schedule a build in " + n + " seconds");
            scheduleBuild(build, n);
        } else {
            LOGGER.log(Level.FINE, "max number of schedules for this build");
        }
    }

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

    /**
     * Wrapper method for mocking purposes.
     */
    public boolean scheduleBuild(AbstractBuild<?, ?> build, int n) {
        ParametersAction p = build.getAction(ParametersAction.class);
        return build.getProject().scheduleBuild(n, new NaginatorCause(), p, new NaginatorAction());
    }

    private boolean parseLog(File logFile, String regexp) throws IOException {

        if (regexp == null) {
            return false;
        }

        // Assume default encoding and text files
        String line;
        Pattern pattern = Pattern.compile(regexp);
        BufferedReader reader = new BufferedReader(new FileReader(logFile));
        while ((line = reader.readLine()) != null) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return true;
            }
        }
        return false;
    }

    private static final Logger LOGGER = Logger.getLogger(NaginatorListener.class.getName());

}
