package com.chikli.hudson.plugin.naginator;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import hudson.model.AbstractBuild;

/**
 * A test suite for {@link ProgressiveDelay}.
 */
public class ProgressiveDelayTest {

    @Test
    public void testComputeScheduleDelay() {
        final ProgressiveDelay progressiveDelay = new ProgressiveDelay(15, 50);
        assertEquals(
                15,
                progressiveDelay.computeScheduleDelay(createBuild(false, null)));
        assertEquals(
                45,
                progressiveDelay.computeScheduleDelay(createBuild(true, null)));
        assertEquals(
                45,
                progressiveDelay.computeScheduleDelay(createBuild(true, createBuild(false, null))));
        // Capped at maximum delay
        assertEquals(
                50,
                progressiveDelay.computeScheduleDelay(createBuild(true, createBuild(true, createBuild(false, null)))));
        // Only consecutive rescheduled builds count
        assertEquals(
                15,
                progressiveDelay.computeScheduleDelay(createBuild(false, createBuild(true, createBuild(false, null)))));
        assertEquals(
                45,
                progressiveDelay.computeScheduleDelay(createBuild(true, createBuild(false, createBuild(true, null)))));
    }

    @Test
    public void testComputeScheduleDelayNoMax() {
        final ProgressiveDelay progressiveDelay = new ProgressiveDelay(15, 0);
        assertEquals(
                15,
                progressiveDelay.computeScheduleDelay(createBuild(false, null)));
        assertEquals(
                45,
                progressiveDelay.computeScheduleDelay(createBuild(true, createBuild(false, null))));
        assertEquals(
                90,
                progressiveDelay.computeScheduleDelay(createBuild(true, createBuild(true, createBuild(false, null)))));
    }

    private static AbstractBuild createBuild(final boolean hasNaginatorAction, final AbstractBuild previousBuild) {
        final AbstractBuild build = mock(AbstractBuild.class);
        when(build.getPreviousBuild()).thenReturn(previousBuild);
        when(build.getAction(NaginatorAction.class)).thenReturn(hasNaginatorAction ? new NaginatorAction(0) : null);
        return build;
    }
}
