package com.chikli.hudson.plugin.naginator;

import hudson.matrix.Combination;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
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
import static hudson.model.Result.ABORTED;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:nicolas.deloof@cloudbees.com">Nicolas De loof</a>
 */
public class NaginatorListener extends RunListener<AbstractBuild<?, ?>> {

	@Override
	public void onCompleted(AbstractBuild<?, ?> build, TaskListener listener) {
		if ((build.getResult() == SUCCESS) || (build.getResult() == ABORTED)) {
			return;
		}

		NaginatorPublisher naginator = build.getProject().getPublishersList()
				.get(NaginatorPublisher.class);

		// JENKINS-13791
		if (naginator == null) {
			return;
		}

		// If we're not set to rerun if unstable, and the build's unstable,
		// return true.
		if ((!naginator.isRerunIfUnstable())
				&& (build.getResult() == Result.UNSTABLE)) {
			return;
		}

		// Do nothing for a single Matrix run. (Run only when all Matrix
		// finishes)
		if (build instanceof MatrixRun) {
			return;
		}

		// If we're supposed to check for a regular expression in the build
		// output before
		// scheduling a new build, do so.
		// if (naginator.isCheckRegexp()) {
		// LOGGER.log(Level.FINEST, "Got checkRegexp == true");

		// String regexpForRerun = naginator.getRegexpForRerun();
		// if ((regexpForRerun !=null) && (!regexpForRerun.equals(""))) {
		// LOGGER.log(Level.FINEST, "regexpForRerun - {0}", regexpForRerun);

		// try {
		// // If parseLog returns false, we didn't find the regular expression,
		// // so return true.
		// if (!parseLog(build.getLogFile(), regexpForRerun)) {
		// LOGGER.log(Level.FINEST, "regexp not in logfile");
		// return;
		// }
		// } catch (IOException e) {
		// e.printStackTrace(listener
		// .error("error while parsing logs for naginator - forcing rebuild."));
		// }
		// }
		// }

		LOGGER.log(Level.FINEST, "Before can Scheldule");
		if (canSchedule(build, naginator)) {
			int n = naginator.getDelay().computeScheduleDelay(build);
			LOGGER.log(
					Level.FINE,
					"about to try to schedule a build #{0} in {1} seconds for {2}",
					new Object[] { build.getNumber(), n,
							build.getProject().getName() });

			List<Combination> combsToRerun = new ArrayList<Combination>();

			if (naginator.isRerunMatrixPart()) {
				if (build instanceof MatrixBuild) {
					MatrixBuild mb = (MatrixBuild) build;
					List<MatrixRun> matrixRuns = mb.getRuns();

					for (MatrixRun r : matrixRuns) {
						if (r.getNumber() == build.getNumber()) {
							if ((r.getResult() == SUCCESS)
									|| (r.getResult() == ABORTED)) {
								continue;
							}
							if ((!naginator.isRerunIfUnstable())
									&& (r.getResult() == Result.UNSTABLE)) {
								continue;
							}
							if (naginator.isCheckRegexp()) {
								LOGGER.log(Level.FINEST,
										"Got checkRegexp == true");

								String regexpForRerun = naginator
										.getRegexpForRerun();
								if ((regexpForRerun != null)
										&& (!regexpForRerun.equals(""))) {
									LOGGER.log(Level.FINEST,
											"regexpForRerun - {0}",
											regexpForRerun);

									try {
										if (!parseLog(r.getLogFile(),
												regexpForRerun)) {
											LOGGER.log(Level.FINEST,
													"regexp not in logfile");
											// return;
											continue;
										}
									} catch (IOException e) {
										e.printStackTrace(listener
												.error("error while parsing logs for naginator - forcing rebuild."));
									}
								}
							}

							LOGGER.log(Level.FINE,
									"add combination to matrix rerun ({0})", r
											.getParent().getCombination()
											.toString());
							combsToRerun.add(r.getParent().getCombination());
						}
					}

				}
			}

			if (!combsToRerun.isEmpty()) {
				LOGGER.log(Level.FINE, "schedule matrix rebuild");
				scheduleMatrixBuild(build, combsToRerun, n);
			} else {
				scheduleBuild(build, n);
			}
		} else {
			LOGGER.log(Level.FINE,
					"max number of schedules for #{0} build, project {1}",
					new Object[] { build.getNumber(),
							build.getProject().getName() });
		}
	}

	public boolean canSchedule(Run build, NaginatorPublisher naginator) {
		Run r = build;
		int max = naginator.getMaxSchedule();
		if (max <= 0)
			return true;
		int n = 0;

		while (r != null && r.getAction(NaginatorAction.class) != null) {
			if (n >= max)
				break;
			r = r.getPreviousBuild();
			n++;
		}

		return n < max;
	}

	public boolean scheduleMatrixBuild(AbstractBuild<?, ?> build,
			List<Combination> combinations, int n) {
		NaginatorMatrixAction nma = new NaginatorMatrixAction();
		for (Combination c : combinations) {
			nma.addCombinationToRerun(c);
		}
		return NaginatorRetryAction.scheduleBuild(build, n, nma);
	}

	/**
	 * Wrapper method for mocking purposes.
	 */
	public boolean scheduleBuild(AbstractBuild<?, ?> build, int n) {
		return NaginatorRetryAction.scheduleBuild(build, n);
	}

	private boolean parseLog(File logFile, String regexp) throws IOException {

		if (regexp == null) {
			return false;
		}

		// Assume default encoding and text files
		String line;
		Pattern pattern = Pattern.compile(regexp);
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(logFile));
			while ((line = reader.readLine()) != null) {
				Matcher matcher = pattern.matcher(line);
				if (matcher.find()) {
					return true;
				}
			}
			return false;
		} finally {
			if (reader != null)
				reader.close();
		}
	}

	private static final Logger LOGGER = Logger
			.getLogger(NaginatorListener.class.getName());

}
