package com.chikli.hudson.plugin.naginator;

import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Reschedules a build if the current one fails.
 *
 * @author Nayan Hajratwala <nayan@chikli.com>
 */
public class NaginatorPublisher extends Notifier {
    private final String regexpForRerun;
    private final boolean rerunIfUnstable;
    private final boolean rerunMatrixPart;
    private final boolean checkRegexp;

    private ScheduleDelay delay;

    private int maxSchedule;

    // backward compatible constructor
    public NaginatorPublisher(String regexpForRerun,
                              boolean rerunIfUnstable,
                              boolean checkRegexp) {
        this(regexpForRerun, rerunIfUnstable, false, checkRegexp, 0, new ProgressiveDelay(5*60, 3*60*60));
    }

    @DataBoundConstructor
    public NaginatorPublisher(String regexpForRerun,
                              boolean rerunIfUnstable,
                              boolean rerunMatrixPart,
                              boolean checkRegexp,
                              int maxSchedule,
                              ScheduleDelay delay) {
        this.regexpForRerun = regexpForRerun;
        this.rerunIfUnstable = rerunIfUnstable;
        this.rerunMatrixPart = rerunMatrixPart;
        this.checkRegexp = checkRegexp;
        this.maxSchedule = maxSchedule;
        this.delay = delay;
    }

    public Object readResolve() {
        if (this.delay == null) {
            // Backward compatibility : progressive 5 minutes up to 3 hours
            delay = new ProgressiveDelay(5*60, 3*60*60);
        }
        return this;
    }

    public boolean isRerunIfUnstable() {
        return rerunIfUnstable;
    }

    public boolean isRerunMatrixPart() {
        return rerunMatrixPart;
    }
    
    public boolean isCheckRegexp() {
        return checkRegexp;
    }

    public String getRegexpForRerun() {
        return regexpForRerun;
    }

    public ScheduleDelay getDelay() {
        return delay;
    }

    public int getMaxSchedule() {
        return maxSchedule;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        if (build instanceof MatrixRun) {
            MatrixBuild parent = ((MatrixRun)build).getParentBuild();
            if (parent.getAction(NaginatorPublisherScheduleAction.class) == null) {
                // No strict exclusion is required
                // as it doesn't matter if the action gets duplicated.
                parent.addAction(new NaginatorPublisherScheduleAction(this));
            }
        } else {
            build.addAction(new NaginatorPublisherScheduleAction(this));
        }
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
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
            return "Retry build after failure";
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
