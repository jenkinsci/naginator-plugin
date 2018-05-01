package com.chikli.hudson.plugin.naginator;

import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixBuild;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
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
    private transient Boolean regexpForMatrixParent;        // for backward compatibility
    private /* almost final */ RegexpForMatrixStrategy regexpForMatrixStrategy;
    private final NoChildStrategy noChildStrategy;
    private NaginatorPublisher naginatorPublisher;
    
    public NaginatorPublisherScheduleAction(NaginatorPublisher publisher) {
        super(publisher.getMaxSchedule(), publisher.getDelay(), publisher.isRerunMatrixPart());
        this.naginatorPublisher = publisher;
        this.regexpForRerun = publisher.getRegexpForRerun();
        this.rerunIfUnstable = publisher.isRerunIfUnstable();
        this.checkRegexp = publisher.isCheckRegexp();
        this.regexpForMatrixStrategy = publisher.getRegexpForMatrixStrategy();
        this.noChildStrategy = publisher.getNoChildStrategy();
    }
    
    public Object readResolve() {
        if (regexpForMatrixStrategy == null) {
            // < 1.17
            if (regexpForMatrixParent != null) {
                regexpForMatrixStrategy = 
                        regexpForMatrixParent.booleanValue()
                        ? RegexpForMatrixStrategy.TestParent
                        : RegexpForMatrixStrategy.TestChildrenRetriggerMatched;
                regexpForMatrixParent = null;
            } else {
                // something strange.
                regexpForMatrixStrategy = RegexpForMatrixStrategy.getDefault();
            }
        }
        return this;
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
    
    /**
     * @deprecated use {@link #getRegexpForMatrixStrategy()}
     */
    @Deprecated
    public boolean isRegexpForMatrixParent() {
        return getRegexpForMatrixStrategy() == RegexpForMatrixStrategy.TestParent;
    }
    
    /**
     * @since 1.17
     */
    @Nonnull
    public RegexpForMatrixStrategy getRegexpForMatrixStrategy() {
        return regexpForMatrixStrategy;
    }
    
    @Override
    public boolean shouldSchedule(@Nonnull Run<?, ?> run, @Nonnull TaskListener listener, int retryCount) {
        if (!checkCommonScheduleThreshold(run)) {
            return false;
        }
        
        // If we're supposed to check for a regular expression in the build output before
        // scheduling a new build, do so.
        if (isCheckRegexp() && (!(run instanceof MatrixBuild) || getRegexpForMatrixStrategy() == RegexpForMatrixStrategy.TestParent)) {
            LOGGER.log(Level.FINEST, "Got checkRegexp == true");
            
            if (!testRegexp(run, listener)) {
                return false;
            }
        } else if (
                isCheckRegexp()
                && (run instanceof MatrixBuild)
                && getRegexpForMatrixStrategy() == RegexpForMatrixStrategy.TestChildrenRetriggerAll
        ) {
            // check should be performed for child builds here.
            if (!testRegexpForFailedChildren((MatrixBuild)run, listener)) {
                return false;
            }
        }
        
        return super.shouldSchedule(run, listener, retryCount);
    }

    private boolean testRegexpForFailedChildren(@Nonnull MatrixBuild run, @Nonnull TaskListener listener) {
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

    @Override
    public boolean shouldScheduleForMatrixRun(@Nonnull MatrixRun run, @Nonnull TaskListener listener) {
        if (!checkCommonScheduleThreshold(run)) {
            return false;
        }
        if (isCheckRegexp() && getRegexpForMatrixStrategy() == RegexpForMatrixStrategy.TestChildrenRetriggerMatched) {
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
                if (!parseLog(run.getLogFile(), run.getCharset(), regexpForRerun)) {
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
    
    private boolean parseLog(final File logFile, final Charset charset, @Nonnull final String regexp) throws IOException {
        // TODO annotate `logFile` with `@Nonnull`
        // after upgrading the target Jenkins to 1.568 or later.
        
        long timeout = getRegexpTimeoutMs();
        
        FutureTask<Boolean> task = new FutureTask<Boolean>(new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return parseLogImpl(logFile, charset, regexp);
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
    
    private static class InterruptibleCharSequence implements CharSequence {
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
    
    /**
     * Extracts the maxSchedule from build log message against the "Search for Log message" regexp.
     *
     * @param message build log message to examine
     * @param regexp string containing regular expression to locate. The data is expected to be in the capture group 1
     * @return the matched integer value
     */
    private int getMessageData(@Nonnull final String message,
        @Nonnull final String regexp) {
        Pattern pattern = Pattern.compile(regexp);
        int data = 0;
        LOGGER.log(Level.FINEST, "Processing message: " + message);
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            data = Integer.parseInt(matcher.group(1));
            // assertThat(data, greaterThan(0));
            LOGGER.log(Level.FINEST, "Extracted data: " + data);
        } else {
            LOGGER.log(Level.FINEST, "Failed to find data in the message: " + message);
        }
        return data;
    }

   private boolean parseLogImpl(File logFile, Charset charset, @Nonnull final String regexp) throws IOException {
        // TODO annotate `logFile` and 'charset' with `@Nonnull`
        // after upgrading the target Jenkins to 1.568 or later.

        // Assume default encoding and text files
        String line;
        Pattern pattern = Pattern.compile(regexp);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(logFile), charset));
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(new InterruptibleCharSequence(line));
                if (matcher.find()) {
                    String message = matcher.group(0);
                    LOGGER.log(Level.FINEST, "Found Log message: " + message);

                    if (naginatorPublisher.isMaxScheduleOverrideAllowed()) {
                        int maxScheduleOverride = getMessageData(message, regexp);
                        if (maxScheduleOverride != 0) {
                            LOGGER.log(Level.FINEST,
                                "Updating maxScheduleOverride: " + maxScheduleOverride);
                            naginatorPublisher.setMaxSchedule(maxScheduleOverride);
                        }
                    }
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
    
    @Override
    @Nonnull
    public NoChildStrategy getNoChildStrategy() {
        return (noChildStrategy != null)
                ? noChildStrategy
                : NoChildStrategy.getDefault();
    }
}