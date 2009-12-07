package com.chikli.hudson.plugin.naginator;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reschedules a build if the current one fails.
 *
 * @author Nayan Hajratwala <nayan@chikli.com>
 */
public class NaginatorPublisher extends Notifier {
    private final String regexpForRerun;
    private final boolean rerunIfUnstable;
    private final boolean checkRegexp;

    private boolean debug = false;
    
    @DataBoundConstructor
    public NaginatorPublisher(String regexpForRerun,
                              boolean rerunIfUnstable,
                              boolean checkRegexp) {
        this.regexpForRerun = regexpForRerun;
        this.checkRegexp = checkRegexp;
        this.rerunIfUnstable = rerunIfUnstable;
    }

    public boolean isRerunIfUnstable() {
        return rerunIfUnstable;
    }

    public boolean isCheckRegexp() {
        return checkRegexp;
    }

    public String getRegexpForRerun() {
        return regexpForRerun;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }
    
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        // If the build was successful, we don't need to Nag, so just return true
        if (build.getResult() == Result.SUCCESS) {
            return true;
        }

        // If we're not set to rerun if unstable, and the build's unstable, return true.
        if ((!rerunIfUnstable) && (build.getResult() == Result.UNSTABLE)) {
            return true;
        }

        // If we're supposed to check for a regular expression in the build output before
        // scheduling a new build, do so.
        if (checkRegexp) {
            if (debug) LOGGER.log(Level.WARNING, "Got checkRegexp == true");
            
            if ((regexpForRerun!=null) && (!regexpForRerun.equals(""))) {
                if (debug) LOGGER.log(Level.WARNING, "regexpForRerun - " + regexpForRerun);
                
                try {
                    // If parseLog returns false, we didn't find the regular expression,
                    // so return true.
                    if (!parseLog(build.getLogFile(),
                                  regexpForRerun)) {
                        if (debug) LOGGER.log(Level.WARNING, "regexp not in logfile");
                        return true;
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

        if (debug) LOGGER.log(Level.WARNING, "about to try to schedule a build");
        return scheduleBuild(build, n);
    }


    /**
     * Wrapper method for mocking purposes.
     */
    public boolean scheduleBuild(AbstractBuild<?, ?> build, int n) throws InterruptedException, IOException {
        // Schedule a new build with the back off
        if (debug) { 
            try {
                build.setDescription("rebuild");
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "couldn't set description: " + e.getStackTrace());
            } 
            return true;
        }
        else {
            return build.getProject().scheduleBuild(n*60, new NaginatorCause());
        }
    }
    
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    private boolean parseLog(File logFile, String regexp) throws IOException,
        InterruptedException {
        
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

    @Override
    public DescriptorImpl getDescriptor() {
        // see Descriptor javadoc for more about what a descriptor is.
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link NaginatorPublisher}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>views/hudson/plugins/naginator/NaginatorBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            super(NaginatorPublisher.class);
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Retry build after failure (Naginator)";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        /**
         * Creates a new instance of {@link NaginatorPublisher} from a submitted form.
         */
        @Override
        public Notifier newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(NaginatorPublisher.class, formData);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(NaginatorPublisher.class.getName());

}
