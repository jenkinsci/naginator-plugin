package com.chikli.hudson.plugin.naginator;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A test suite for {@link ProgressiveDelay}.
 */
@WithJenkins
class ProgressiveDelayTest {

    private static JenkinsRule j;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testComputeScheduleDelay() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());
        p.getPublishersList().add(new NaginatorPublisher(
            "",         // regexpForRerun
            true,       // rerunIfUnstable
            false,      // retunForMatrixPart
            false,      // checkRegexp
            2,          // maxSchedule
            new FixedDelay(0)
        ));
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();

        FreeStyleBuild build1 = p.getFirstBuild();
        FreeStyleBuild build2 = build1.getNextBuild();
        assertNotNull(build2);
        FreeStyleBuild build3 = build2.getNextBuild();

        final ProgressiveDelay progressiveDelay = new ProgressiveDelay(15, 50);
        assertEquals(
                15,
                progressiveDelay.computeScheduleDelay(build1));
        assertEquals(
                45,
                progressiveDelay.computeScheduleDelay(build2));
        // Capped at maximum delay
        assertEquals(
                50,
                progressiveDelay.computeScheduleDelay(build3));
    }

    @Test
    void testComputeScheduleDelayNoMax() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());
        p.getPublishersList().add(new NaginatorPublisher(
            "",         // regexpForRerun
            true,       // rerunIfUnstable
            false,      // retunForMatrixPart
            false,      // checkRegexp
            2,          // maxSchedule
            new FixedDelay(0)
        ));
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();

        FreeStyleBuild build1 = p.getFirstBuild();
        FreeStyleBuild build2 = build1.getNextBuild();
        assertNotNull(build2);
        FreeStyleBuild build3 = build2.getNextBuild();

        final ProgressiveDelay progressiveDelay = new ProgressiveDelay(15, 0);
        assertEquals(
                15,
                progressiveDelay.computeScheduleDelay(build1));
        assertEquals(
                45,
                progressiveDelay.computeScheduleDelay(build2));
        assertEquals(
                90,
                progressiveDelay.computeScheduleDelay(build3));
    }

    @Issue("JENKINS-43803")
    @Test
    void testBuildsRunAlternately() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());

        FreeStyleBuild buildA1 = p.scheduleBuild2(0).get();
        assertTrue(NaginatorRetryAction.scheduleBuild(
            buildA1,
            0,  // delay
            1,  // retryCount
            120   // maxRetryCount
        ));
        j.waitUntilNoActivity();
        FreeStyleBuild buildA2 = p.getLastBuild();

        p.scheduleBuild2(0).get();     // buildB1

        assertTrue(NaginatorRetryAction.scheduleBuild(
            buildA2,
            0,  // delay
            2,  // retyCount
            120   // maxRetryCount
        ));

        j.waitUntilNoActivity();
        FreeStyleBuild buildA3 = p.getLastBuild();

        ProgressiveDelay delay = new ProgressiveDelay(300, 0);
        assertEquals(
            Arrays.asList(
                300,
                900,
                1800
            ),
            Arrays.asList(
                delay.computeScheduleDelay(buildA1),
                delay.computeScheduleDelay(buildA2),
                delay.computeScheduleDelay(buildA3)
            )
        );
    }
}
