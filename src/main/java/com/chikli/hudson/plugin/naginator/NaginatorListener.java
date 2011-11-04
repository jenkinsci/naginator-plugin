package com.chikli.hudson.plugin.naginator;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:nicolas.deloof@cloudbees.com">Nicolas De loof</a>
 */
public class NaginatorListener extends RunListener<AbstractBuild<?,?>> {


    @Override
    public void onCompleted(AbstractBuild<?, ?> build, TaskListener listener) {
        if (build.getResult() == Result.SUCCESS) {
            return;
        }

        NaginatorPublisher naginator = build.getProject().getPublishersList().get(NaginatorPublisher.class);

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

        // if a build fails for a reason that cannot be immediately fixed,
        // immediate rescheduling may cause a very tight loop.
        // combined with publishers like e-mail, IM, this could flood the users.
        //
        // so to avoid this problem, progressively introduce delay until the next build

        // delay = the number of consective build problems * 5 mins
        // back off at most 3 hours
        int n=0;
        for(AbstractBuild<?,?> b=build; b!=null && b.getResult()!=Result.SUCCESS && n<60; b=b.getPreviousBuild())
            n+=5;

        LOGGER.log(Level.FINE, "about to try to schedule a build");
        scheduleBuild(build, n);
    }


    /**
     * Wrapper method for mocking purposes.
     */
    public boolean scheduleBuild(AbstractBuild<?, ?> build, int n) {
        return build.getProject().scheduleBuild(n*60, new NaginatorCause());
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
