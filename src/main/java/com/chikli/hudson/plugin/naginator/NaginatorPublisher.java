package com.chikli.hudson.plugin.naginator;

import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * Reschedules a build if the current one fails.
 *
 * @author Nayan Hajratwala <nayan@chikli.com>
 */
public class NaginatorPublisher extends Notifier {
    public final static long DEFAULT_REGEXP_TIMEOUT_MS = 30000;
    
    private final String regexpForRerun;
    private final boolean rerunIfUnstable;
    private final boolean rerunMatrixPart;
    private final boolean checkRegexp;
    @Deprecated
    private transient Boolean regexpForMatrixParent;
    private RegexpForMatrixStrategy regexpForMatrixStrategy;    /* almost final */
    private NoChildStrategy noChildStrategy;    /* almost final */

    private ScheduleDelay delay;

    private int maxSchedule;

    // backward compatible constructor
    public NaginatorPublisher(String regexpForRerun,
                              boolean rerunIfUnstable,
                              boolean checkRegexp) {
        this(regexpForRerun, rerunIfUnstable, false, checkRegexp, 0, new ProgressiveDelay(5*60, 3*60*60));
    }

    /**
     * constructor.
     */
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
        setRegexpForMatrixStrategy(RegexpForMatrixStrategy.TestParent); // backward compatibility with < 1.16
    }
    
    /**
     * @since 1.16
     * @deprecated use {@link #NaginatorPublisher(String, boolean, boolean, boolean, int, ScheduleDelay)} and other setters
     */
    @Deprecated
    public NaginatorPublisher(String regexpForRerun,
                              boolean rerunIfUnstable,
                              boolean rerunMatrixPart,
                              boolean checkRegexp,
                              boolean regexpForMatrixParent,
                              int maxSchedule,
                              ScheduleDelay delay) {
        this(regexpForRerun, rerunIfUnstable, rerunMatrixPart, checkRegexp, maxSchedule, delay);
        setRegexpForMatrixParent(regexpForMatrixParent);
    }

    public Object readResolve() {
        if (this.delay == null) {
            // Backward compatibility : progressive 5 minutes up to 3 hours
            delay = new ProgressiveDelay(5*60, 3*60*60);
        }
        if (regexpForMatrixStrategy == null) {
            if (regexpForMatrixParent != null) {
                // >= 1.16
                setRegexpForMatrixParent(regexpForMatrixParent.booleanValue());
                regexpForMatrixParent = null;
            } else {
                // < 1.16
                setRegexpForMatrixStrategy(RegexpForMatrixStrategy.TestParent);
            }
        }
        return this;
    }

    public boolean isRerunIfUnstable() {
        return rerunIfUnstable;
    }

    public boolean isRerunMatrixPart() {
        return rerunMatrixPart;
    }
    
    /**
     * @param noChildStrategy
     * 
     * @since 1.17
     */
    @DataBoundSetter
    public void setNoChildStrategy(@Nonnull NoChildStrategy noChildStrategy) {
        this.noChildStrategy = noChildStrategy;
    }
    
    /**
     * @return the strategy for no children to rerun for a matrix project.
     * 
     * @since 1.17
     */
    @Nonnull
    public NoChildStrategy getNoChildStrategy() {
        return (noChildStrategy != null)
                ? noChildStrategy
                : NoChildStrategy.getDefault();
    }
    
    public boolean isCheckRegexp() {
        return checkRegexp;
    }

    /**
     * Returns whether apply the regexp to the matrix parent instead of matrix children.
     * 
     * The default is <code>false</code> for naginator-plugin >= 1.16
     * though <code>true</code> for configurations upgraded from naginator-plugin < 1.16.
     * 
     * @return Returns whether apply the regexp to the matrix parent instead of matrix children
     * @since 1.16
     * @deprecated use {@link #getRegexpForMatrixStrategy()}
     */
    @Deprecated
    public boolean isRegexpForMatrixParent() {
        return (getRegexpForMatrixStrategy() == RegexpForMatrixStrategy.TestParent);
    }

    private void setRegexpForMatrixParent(boolean regexpForMatrixParent) {
        setRegexpForMatrixStrategy(
                regexpForMatrixParent
                ? RegexpForMatrixStrategy.TestParent
                : RegexpForMatrixStrategy.TestChildrenRetriggerMatched  // compatible with 1.16.
        );
    }

    public String getRegexpForRerun() {
        return regexpForRerun;
    }

    /**
     * @param regexpForMatrixStrategy
     * @since 1.17
     */
    @DataBoundSetter
    public void setRegexpForMatrixStrategy(@Nonnull RegexpForMatrixStrategy regexpForMatrixStrategy) {
        this.regexpForMatrixStrategy = regexpForMatrixStrategy;
    }

    /**
     * @return how to apply regexp for matrix builds.
     * @since 1.17
     */
    @Nonnull
    public RegexpForMatrixStrategy getRegexpForMatrixStrategy() {
        return regexpForMatrixStrategy;
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
            if (
                    getRegexpForMatrixStrategy() == RegexpForMatrixStrategy.TestChildrenRetriggerMatched
                    && !isRerunMatrixPart()
            ) {
                listener.getLogger().println("[Naginator] Warning: TestChildrenRetriggerMatched doesn't work without rerunMatrixPart");
            }
            
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
        private long regexpTimeoutMs;

        public DescriptorImpl() {
            // default value
            regexpTimeoutMs = DEFAULT_REGEXP_TIMEOUT_MS;
            load();
        }

        /**
         * @return timeout for regular expressions.
         * @since 1.16.1
         */
        public long getRegexpTimeoutMs() {
            return regexpTimeoutMs;
        }

        /**
         * @param regexpTimeoutMs timeout for regular expressions.
         * @since 1.16.1
         */
        public void setRegexpTimeoutMs(long regexpTimeoutMs) {
            this.regexpTimeoutMs = regexpTimeoutMs;
        }

        /**
         * @see hudson.model.Descriptor#configure(org.kohsuke.stapler.StaplerRequest, net.sf.json.JSONObject)
         */
        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws hudson.model.Descriptor.FormException {
            setRegexpTimeoutMs(json.getLong("regexpTimeoutMs"));
            boolean result = super.configure(req, json);
            save();
            return result;
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
        
        /**
         * @return true if the current request is for a matrix project.
         */
        public boolean isMatrixProject(Object it) {
            return (it instanceof MatrixProject);
        }
        
        public FormValidation doCheckRegexpForMatrixStrategy(
                @QueryParameter RegexpForMatrixStrategy value,
                @QueryParameter boolean rerunMatrixPart
        ) {
            // called only when regexpForMatrixStrategy is displayed,
            // and we can assume that this is a matrix project.
            if (value == RegexpForMatrixStrategy.TestChildrenRetriggerMatched && !rerunMatrixPart) {
                return FormValidation.warning(Messages.NaginatorPublisher_RegexpForMatrixStrategy_RerunMatrixPartShouldBeEnabled());
            }
            return FormValidation.ok();
        }
        
        public ListBoxModel doFillRegexpForMatrixStrategyItems() {
            ListBoxModel ret = new ListBoxModel();
            for (RegexpForMatrixStrategy strategy: RegexpForMatrixStrategy.values()) {
                ret.add(strategy.getDisplayName(), strategy.name());
            }
            return ret;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(NaginatorPublisher.class.getName());

}
