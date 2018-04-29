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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Boolean.parseBoolean;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import javax.annotation.Nonnull;

@SuppressWarnings("unused")
public class NaginatorPublisher extends Notifier {
	public final static long DEFAULT_REGEXP_TIMEOUT_MS = 30000;

	private final String regexpForRerun;
	private final boolean rerunIfUnstable;
	private final boolean rerunMatrixPart;
	private final boolean checkRegexp;
	private final boolean maxScheduleOverrideAllowed;
	@Deprecated
	private transient Boolean regexpForMatrixParent;
	private RegexpForMatrixStrategy regexpForMatrixStrategy; /* almost final */
	private NoChildStrategy noChildStrategy; /* almost final */

	private ScheduleDelay delay;

	private int maxSchedule; // possibly gets overriden after the build run

	// backward compatible constructor
	public NaginatorPublisher(String regexpForRerun, boolean rerunIfUnstable,
			boolean checkRegexp) {
		this(regexpForRerun, rerunIfUnstable, false, checkRegexp, false, 0,
				new ProgressiveDelay(5 * 60, 3 * 60 * 60));
	}

	/**
	 * Constructor
	 *
	 * @param regexpForRerun regular expression to scan build log
	 * @param rerunIfUnstable whether to rerun unstable builds
	 * @param rerunMatrixPart whether to rerun matrix build
	 * @param checkRegexp use the regexpForRerun to determine whether to rerun the failing build
	 * @param maxScheduleOverrideAllowed extract the maxSchedule with the help of the regexpForRerun
	 * @param maxSchedule maximum number of consecutive reruns
	 * @param delay delay between reruns
	 */

	@DataBoundConstructor
	public NaginatorPublisher(String regexpForRerun, boolean rerunIfUnstable,
			boolean rerunMatrixPart, boolean checkRegexp,
			boolean maxScheduleOverrideAllowed, int maxSchedule,
			ScheduleDelay delay) {
		this.regexpForRerun = regexpForRerun;
		// extract from regexpForRerun
		// TODO: defer to testRegexp()
		Pattern pattern = Pattern.compile(this.regexpForRerun);
		// dummy
		java.util.regex.Matcher matcher = pattern.matcher("");
		assertFalse(matcher.find());

		this.rerunIfUnstable = rerunIfUnstable;
		this.rerunMatrixPart = rerunMatrixPart;
		this.checkRegexp = checkRegexp;
		this.maxSchedule = maxSchedule;
		this.maxScheduleOverrideAllowed = maxScheduleOverrideAllowed;
		this.delay = delay;
		setRegexpForMatrixStrategy(RegexpForMatrixStrategy.TestParent); // backward
																																		// 1.16
	}

	/**
	 * Constructor
	 *
	 * @param regexpForRerun regular expression to scan build log
	 * @param rerunIfUnstable whether to rerun unstable builds
	 * @param rerunMatrixPart whether to rerun matrix build
	 * @param checkRegexp use the regexpForRerun to determine whether to rerun the failing build
	 * @param regexpForMatrixParent analog of regexpForRerun provided for the parent build
	 * @param maxScheduleOverrideAllowed extract the maxSchedule with the help of the regexpForRerun
	 * @param maxSchedule maximum number of consecutive reruns
	 * @param delay delay between reruns
	 */
	@Deprecated
	public NaginatorPublisher(String regexpForRerun, boolean rerunIfUnstable,
			boolean rerunMatrixPart, boolean checkRegexp,
			boolean maxScheduleOverrideAllowed, boolean regexpForMatrixParent,
			int maxSchedule, ScheduleDelay delay) {
		this(regexpForRerun, rerunIfUnstable, rerunMatrixPart, checkRegexp,
				maxScheduleOverrideAllowed, maxSchedule, delay);
		setRegexpForMatrixParent(regexpForMatrixParent);
	}

	//
	public Object readResolve() {
		if (this.delay == null) {
			// Backward compatibility : progressive 5 minutes up to 3 hours
			delay = new ProgressiveDelay(5 * 60, 3 * 60 * 60);
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

	public boolean isMaxScheduleOverrideAllowed() {
		return maxScheduleOverrideAllowed;
	}

	public boolean isRerunIfUnstable() {
		return rerunIfUnstable;
	}

	public boolean isRerunMatrixPart() {
		return rerunMatrixPart;
	}

	// https://www.programcreek.com/java-api-examples/index.php?api=org.kohsuke.stapler.DataBoundSetter
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
		return (noChildStrategy != null) ? noChildStrategy
				: NoChildStrategy.getDefault();
	}

	public boolean isCheckRegexp() {
		return checkRegexp;
	}

	@Deprecated
	public boolean isRegexpForMatrixParent() {
		return (getRegexpForMatrixStrategy() == RegexpForMatrixStrategy.TestParent);
	}

	private void setRegexpForMatrixParent(boolean regexpForMatrixParent) {
		setRegexpForMatrixStrategy(
				regexpForMatrixParent ? RegexpForMatrixStrategy.TestParent
						: RegexpForMatrixStrategy.TestChildrenRetriggerMatched // compatible
																																		// with
																																		// 1.16.
		);
	}

	public String getRegexpForRerun() {
		return regexpForRerun;
	}

	/**
	 * Sets the maxSchedule property, initially filled from data bound comstructor e.g. from the matched build log message.
	 *
	 * @param value new maxSchedule
	 * @see getMaxSchedule
	 */
	public void setMaxSchedule(int value) {
		this.maxSchedule = value;
	}

	@DataBoundSetter
	public void setRegexpForMatrixStrategy(
			@Nonnull RegexpForMatrixStrategy regexpForMatrixStrategy) {
		this.regexpForMatrixStrategy = regexpForMatrixStrategy;
	}

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
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException {
		if (build instanceof MatrixRun) {
			if (getRegexpForMatrixStrategy() == RegexpForMatrixStrategy.TestChildrenRetriggerMatched
					&& !isRerunMatrixPart()) {
				listener.getLogger().println(
						"[Naginator] Warning: TestChildrenRetriggerMatched doesn't work without rerunMatrixPart");
			}

			MatrixBuild parent = ((MatrixRun) build).getParentBuild();
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

	@Extension
	public static final class DescriptorImpl
			extends BuildStepDescriptor<Publisher> {
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
		public boolean configure(StaplerRequest req, JSONObject json)
				throws hudson.model.Descriptor.FormException {
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
		public Notifier newInstance(StaplerRequest req, JSONObject formData)
				throws FormException {
			return req.bindJSON(NaginatorPublisher.class, formData);
		}

		public boolean isMatrixProject(Object it) {
			return (it instanceof MatrixProject);
		}

		public FormValidation doCheckRegexpForMatrixStrategy(
				@QueryParameter RegexpForMatrixStrategy value,
				@QueryParameter boolean rerunMatrixPart) {
			// called only when regexpForMatrixStrategy is displayed,
			// and we can assume that this is a matrix project.
			if (value == RegexpForMatrixStrategy.TestChildrenRetriggerMatched
					&& !rerunMatrixPart) {
				return FormValidation.warning(Messages
						.NaginatorPublisher_RegexpForMatrixStrategy_RerunMatrixPartShouldBeEnabled());
			}
			return FormValidation.ok();
		}

		public ListBoxModel doFillRegexpForMatrixStrategyItems() {
			ListBoxModel ret = new ListBoxModel();
			for (RegexpForMatrixStrategy strategy : RegexpForMatrixStrategy
					.values()) {
				ret.add(strategy.getDisplayName(), strategy.name());
			}
			return ret;
		}
	}

	private static final Logger LOGGER = Logger
			.getLogger(NaginatorPublisher.class.getName());

}
