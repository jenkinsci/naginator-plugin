package com.chikli.hudson.plugin.naginator;

import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixBuild;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jenkins.model.Jenkins;

/**
 * Used from {@link NaginatorPublisher} to mark a build to be reshceduled.
 * 
 * @since 1.16
 */
public class NaginatorPublisherScheduleAction extends NaginatorScheduleAction {
    private static final Logger LOGGER = Logger.getLogger(NaginatorPublisherScheduleAction.class.getName());
    
    private final String regexpForRerun;
    private final boolean rerunIfUnstable;
    private final boolean checkRegexp;
    private final boolean regexpForMatrixParent;
    
    public NaginatorPublisherScheduleAction(NaginatorPublisher publisher) {
        super(publisher.getMaxSchedule(), publisher.getDelay(), publisher.isRerunMatrixPart());
        this.regexpForRerun = publisher.getRegexpForRerun();
        this.rerunIfUnstable = publisher.isRerunIfUnstable();
        this.regexpForMatrixParent = publisher.isRegexpForMatrixParent();
        this.checkRegexp = publisher.isCheckRegexp();
    }
    
    @CheckForNull
    public String getRegexpForRerun() {
        return regexpForRerun;
    }
    
    public boolean isRerunIfUnstable() {
        return rerunIfUnstable;
    }
    
    public boolean isCheckRegexp() {
        return checkRegexp;
    }
    
    public boolean isRegexpForMatrixParent() {
        return regexpForMatrixParent;
    }
    
    @Override
    public boolean shouldSchedule(@Nonnull Run<?, ?> run, @Nonnull TaskListener listener, int retryCount) {
        if (!checkCommonScheduleThreshold(run)) {
            return false;
        }
        
        // If we're supposed to check for a regular expression in the build output before
        // scheduling a new build, do so.
        if (isCheckRegexp() && (!(run instanceof MatrixBuild) || isRegexpForMatrixParent())) {
            LOGGER.log(Level.FINEST, "Got checkRegexp == true");
            
            if (!testRegexp(run, listener)) {
                return false;
            }
        } else if (
                isCheckRegexp()
                && (run instanceof MatrixBuild)
                && !isRegexpForMatrixParent()
                && !isRerunMatrixPart()
        ) {
            // check should be performed for child builds here.
            for (MatrixRun r : ((MatrixBuild)run).getExactRuns()) {
                if (!checkCommonScheduleThreshold(r)) {
                    continue;
                }
                if (testRegexp(r, listener)) {
                    return true;
                }
            }
            // no children matched.
            return false;
        }
        
        return super.shouldSchedule(run, listener, retryCount);
    }

    @Override
    public boolean shouldScheduleForMatrixRun(@Nonnull MatrixRun run, @Nonnull TaskListener listener) {
        if (!checkCommonScheduleThreshold(run)) {
            return false;
        }
        if (isCheckRegexp() && !isRegexpForMatrixParent()) {
            LOGGER.log(Level.FINEST, "Got isRerunMatrixPart == true");
            
            if (!testRegexp(run, listener)) {
                return false;
            }
        }
        return true;
    }
    
    private boolean checkCommonScheduleThreshold(@Nonnull Run<?, ?> run) {
        if ((run.getResult() == Result.SUCCESS) || (run.getResult() == Result.ABORTED)) {
            return false;
        }
        
        // If we're not set to rerun if unstable, and the build's unstable, return true.
        if ((!isRerunIfUnstable()) && (run.getResult() == Result.UNSTABLE)) {
            return false;
        }
        return true;
    }
    
    private boolean testRegexp(@Nonnull Run<?, ?> run, TaskListener listener) {
        String regexpForRerun = getRegexpForRerun();
        if ((regexpForRerun != null) && (!regexpForRerun.equals(""))) {
            LOGGER.log(Level.FINEST, "regexpForRerun - {0}", regexpForRerun);
            
            try {
                // If parseLog returns false, we didn't find the regular expression,
                // so return true.
                if (!parseLog(run.getLogFile(), regexpForRerun)) {
                    LOGGER.log(Level.FINEST, "regexp not in logfile");
                    return false;
                }
            } catch (IOException e) {
                e.printStackTrace(listener
                                  .error("error while parsing logs for naginator - forcing rebuild."));
            }
        }
        return true;
    }
    
    private long getRegexpTimeoutMs() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return NaginatorPublisher.DEFAULT_REGEXP_TIMEOUT_MS;
        }
        NaginatorPublisher.DescriptorImpl d = (NaginatorPublisher.DescriptorImpl)j.getDescriptor(NaginatorPublisher.class);
        if (d == null) {
            return NaginatorPublisher.DEFAULT_REGEXP_TIMEOUT_MS;
        }
        return d.getRegexpTimeoutMs();
    }
    
    private boolean parseLog(final File logFile, @Nonnull final String regexp) throws IOException {
        // TODO annotate `logFile` with `@Nonnull`
        // after upgrading the target Jenkins to 1.568 or later.
        
        long timeout = getRegexpTimeoutMs();
        
        FutureTask<Boolean> task = new FutureTask<Boolean>(new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return parseLogImpl(logFile, regexp);
            }
        });
        
        Thread t = new Thread(task);
        t.start();
        
        try {
            // never null
            return task.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOGGER.log(
                    Level.WARNING,
                    String.format("Aborted regexp '%s' for too long execution time ( > %d ms).", regexp, timeout)
            );
        } catch (InterruptedException e) {
            LOGGER.log(
                    Level.SEVERE,
                    String.format("Aborted regexp '%s'", regexp),
                    e
            );
        } catch (ExecutionException e) {
            LOGGER.log(
                    Level.SEVERE,
                    String.format("Aborted regexp '%s'", regexp),
                    e
            );
        }
        if (t.isAlive()) {
            t.interrupt();
        }
        try {
            t.join();
        } catch (InterruptedException e) {
            // ok
        }
        return false;
    }
    
    private class InterruptibleCharSequence implements CharSequence {
        private final CharSequence wrapped;
        
        public InterruptibleCharSequence(CharSequence wrapped) {
            this.wrapped = wrapped;
        }
        
        public int length() {
            return wrapped.length();
        }
        
        public char charAt(int index) {
            if (Thread.currentThread().isInterrupted()) {
                throw new RuntimeException(new InterruptedException());
            }
            return wrapped.charAt(index);
        }
        
        public CharSequence subSequence(int start, int end) {
            return wrapped.subSequence(start, end);
        }
    }
    
    private boolean parseLogImpl(final File logFile, @Nonnull final String regexp) throws IOException {
        // TODO annotate `logFile` with `@Nonnull`
        // after upgrading the target Jenkins to 1.568 or later.

        // Assume default encoding and text files
        String line;
        Pattern pattern = Pattern.compile(regexp);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(logFile));
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(new InterruptibleCharSequence(line));
                if (matcher.find()) {
                    return true;
                }
            }
            return false;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
}
