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

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        // Nothing to do during the build, see NaginatorListener
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }


    @Override
    public DescriptorImpl getDescriptor() {
        // see Descriptor javadoc for more about what a descriptor is.
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public final static NaginatorListener LISTENER = new NaginatorListener();


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
