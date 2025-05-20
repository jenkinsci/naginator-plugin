/*
 * The MIT License
 *
 * Copyright (c) 2015 IKEDA Yasuyuki
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.chikli.hudson.plugin.naginator;

import com.chikli.hudson.plugin.naginator.testutils.MyBuilder;
import hudson.AbortException;
import hudson.Launcher;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.Combination;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixRun;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.Builder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.IOException;
import java.lang.reflect.Field;

import static com.chikli.hudson.plugin.naginator.testutils.TestSupport.lastBuildNumber;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link NaginatorPublisher}.
 * Many tests for {@link NaginatorPublisher} are also in {@link NaginatorListenerTest}
 */
@WithJenkins
class NaginatorPublisherTest {

    private static JenkinsRule j;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        j = rule;
    }

    private static class FailSpecificAxisBuilder extends Builder {
        private final String combinationFilterToFail;

        public FailSpecificAxisBuilder(String combinationFilterToFail) {
            this.combinationFilterToFail = combinationFilterToFail;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            if (!(build instanceof MatrixRun run)) {
                throw new AbortException("only applicable for MatrixRun");
            }

            if (run.getParent().getCombination().evalGroovyExpression(
                    run.getParent().getParent().getAxes(),
                    combinationFilterToFail
            )) {
                throw new AbortException("Combination matches the filter.");
            }

            return true;
        }
    }

    /**
     * Disabling {@link NaginatorPublisher#isRerunIfUnstable()}
     * should trigger all children.
     *
     * @throws Exception
     */
    @Test
    void testMatrixBuildWithoutRerunMatrixPart() throws Exception {
        final int maxSchedule = 2;

        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "1", "2"),
                new Axis("axis2", "1", "2")
        );
        p.setAxes(axes);
        p.getBuildersList().add(new FailSpecificAxisBuilder("(axis1=='1' && axis2=='2') || (axis1=='2' && axis2=='1')"));
        p.getPublishersList().add(new NaginatorPublisher(
                null,   // regexpForRerun
                false,  // rerunIfUnstable
                false,  // rerunMatrixPart
                false,  // checkRegexp
                maxSchedule,
                new FixedDelay(0)
        ));

        p.scheduleBuild2(0);
        j.waitUntilNoActivity();

        assertEquals(maxSchedule + 1, lastBuildNumber(p));

        // (1, 1), (1, 2), (2, 1), (2, 2) are rescheduled.
        MatrixBuild b = p.getLastBuild();
        assertNotNull(b);
        assertNotNull(b.getExactRun(new Combination(axes, "1", "1")));
        assertNotNull(b.getExactRun(new Combination(axes, "1", "2")));
        assertNotNull(b.getExactRun(new Combination(axes, "2", "1")));
        assertNotNull(b.getExactRun(new Combination(axes, "2", "2")));
    }

    /**
     * Enabling {@link NaginatorPublisher#isRerunIfUnstable()}
     * should trigger only failed children.
     *
     * @throws Exception
     */
    @Test
    void testMatrixBuildWithRerunMatrixPart() throws Exception {
        final int maxSchedule = 2;

        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "1", "2"),
                new Axis("axis2", "1", "2")
        );
        p.setAxes(axes);
        p.getBuildersList().add(new FailSpecificAxisBuilder("(axis1=='1' && axis2=='2') || (axis1=='2' && axis2=='1')"));
        p.getPublishersList().add(new NaginatorPublisher(
                null,   // regexpForRerun
                false,  // rerunIfUnstable
                true,  // rerunMatrixPart
                false,  // checkRegexp
                maxSchedule,
                new FixedDelay(0)
        ));

        p.scheduleBuild2(0);
        j.waitUntilNoActivity();

        assertEquals(maxSchedule + 1, lastBuildNumber(p));

        // (1, 2), (2, 1) are rescheduled.
        MatrixBuild b = p.getLastBuild();
        assertNotNull(b);
        assertNull(b.getExactRun(new Combination(axes, "1", "1")));
        assertNotNull(b.getExactRun(new Combination(axes, "1", "2")));
        assertNotNull(b.getExactRun(new Combination(axes, "2", "1")));
        assertNull(b.getExactRun(new Combination(axes, "2", "2")));
    }

    /**
     * Tests the behavior when <code>regexpForMatrixStrategy</code> is <code>TestParent</code>
     * and the log match the regular expression.
     *
     * @throws Exception
     */
    @Test
    void testRegexpForMatrixParentMatches() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "value1", "value2", "value3")
        );
        p.setAxes(axes);

        // only axis1=value2 fails.
        p.getBuildersList().add(new FailSpecificAxisBuilder("axis1 == 'value2'"));
        NaginatorPublisher naginator = new NaginatorPublisher(
                "value2 completed with result FAILURE",
                false,  // rerunIfUnstable
                true,   // retunMatrixPart
                true,   // checkRegexp
                1,      // maxSchedule
                new FixedDelay(0)
        );
        naginator.setRegexpForMatrixStrategy(RegexpForMatrixStrategy.TestParent);
        p.getPublishersList().add(naginator);

        p.scheduleBuild2(0);
        j.waitUntilNoActivity();

        // build rescheduled.
        assertEquals(2, lastBuildNumber(p));
    }

    /**
     * Tests the behavior when <code>regexpForMatrixStrategy</code> is <code>TestParent</code>
     * but the log doesn't match the regular expression.
     *
     * @throws Exception
     */
    @Test
    void testRegexpForMatrixParentNotMatches() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "value1", "value2", "value3")
        );
        p.setAxes(axes);

        // only axis1=value3 fails.
        p.getBuildersList().add(new FailSpecificAxisBuilder("axis1 == 'value3'"));
        NaginatorPublisher naginator = new NaginatorPublisher(
                "value2 completed with result FAILURE",
                false,  // rerunIfUnstable
                true,   // retunMatrixPart
                true,   // checkRegexp
                1,      // maxSchedule
                new FixedDelay(0)
        );
        naginator.setRegexpForMatrixStrategy(RegexpForMatrixStrategy.TestParent);
        p.getPublishersList().add(naginator);

        p.scheduleBuild2(0);
        j.waitUntilNoActivity();

        // build rescheduled.
        assertEquals(1, lastBuildNumber(p));
    }

    /**
     * Tests the behavior when <code>regexpForMatrixStrategy</code> is <code>TestChildrenRetriggerMatched</code>.
     *
     * @throws Exception
     */
    @Test
    void testRegexpForMatrixChild() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "value1", "value2", "value3")
        );
        p.setAxes(axes);

        // all axis fails, and outputs "I am (axis value)"
        p.getBuildersList().add(new MyBuilder("I am ${axis1}", Result.FAILURE));
        NaginatorPublisher naginator = new NaginatorPublisher(
                "I am value[13]",
                false,  // rerunIfUnstable
                true,   // retunMatrixPart
                true,   // checkRegexp
                1,      // maxSchedule
                new FixedDelay(0)
        );
        naginator.setRegexpForMatrixStrategy(RegexpForMatrixStrategy.TestChildrenRetriggerMatched);
        p.getPublishersList().add(naginator);

        p.scheduleBuild2(0);
        j.waitUntilNoActivity();

        // build rescheduled.
        assertEquals(2, lastBuildNumber(p));

        // only axis1=value1, axis1=value3 is rescheduled for the regular expression.
        MatrixBuild b = p.getLastBuild();
        assertNotNull(b);
        assertNotNull(b.getExactRun(new Combination(axes, "value1")));
        assertNull(b.getExactRun(new Combination(axes, "value2")));
        assertNotNull(b.getExactRun(new Combination(axes, "value3")));
    }

    /**
     * Tests the behavior when <code>regexpForMatrixStrategy</code> is <code>TestChildrenRetriggerMatched</code>
     * and <code>retunMatrixPart</code> is <code>false</code>.
     *
     * @throws Exception
     */
    @Test
    void testRegexpForMatrixChildWithoutMatrixPart() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "value1", "value2", "value3")
        );
        p.setAxes(axes);

        // all axis fails, and outputs "I am (axis value)"
        p.getBuildersList().add(new MyBuilder("I am ${axis1}", Result.FAILURE));
        NaginatorPublisher naginator = new NaginatorPublisher(
                "thatcannotbe",
                false,  // rerunIfUnstable
                false,  // retunMatrixPart
                true,   // checkRegexp
                1,      // maxSchedule
                new FixedDelay(0)
        );
        naginator.setRegexpForMatrixStrategy(RegexpForMatrixStrategy.TestChildrenRetriggerMatched);
        naginator.setNoChildStrategy(NoChildStrategy.DontRun);
        p.getPublishersList().add(naginator);

        p.scheduleBuild2(0);
        j.waitUntilNoActivity();

        // build is rescheduled
        // as regular expression is not applied actually.
        assertEquals(2, lastBuildNumber(p));
    }

    /**
     * Tests the behavior when <code>regexpForMatrixStrategy</code> is <code>TestChildrenRetriggerAll</code>.
     *
     * @throws Exception
     */
    @Test
    void testRegexpForMatrixChildAndRetriggerAll() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "value1", "value2", "value3")
        );
        p.setAxes(axes);

        // axis1=value1 and axis1=value3 fails, and outputs "I am (axis value)"
        p.getBuildersList().add(new MyBuilder("I am ${axis1}", "axis1 in ['value2']"));
        NaginatorPublisher naginator = new NaginatorPublisher(
                "I am value1",
                false,  // rerunIfUnstable
                true,   // retunMatrixPart
                true,   // checkRegexp
                1,      // maxSchedule
                new FixedDelay(0)
        );
        naginator.setRegexpForMatrixStrategy(RegexpForMatrixStrategy.TestChildrenRetriggerAll);
        p.getPublishersList().add(naginator);

        p.scheduleBuild2(0);
        j.waitUntilNoActivity();

        // build rescheduled.
        assertEquals(2, lastBuildNumber(p));

        // only axis1=value1, axis1=value3 is rescheduled for the failure.
        MatrixBuild b = p.getLastBuild();
        assertNotNull(b);
        assertNotNull(b.getExactRun(new Combination(axes, "value1")));
        assertNull(b.getExactRun(new Combination(axes, "value2")));
        assertNotNull(b.getExactRun(new Combination(axes, "value3")));
    }

    /**
     * Tests the behavior when <code>regexpForMatrixStrategy</code> is <code>TestChildrenRetriggerAll</code>
     * and <code>retunMatrixPart</code> is <code>false</code>.
     *
     * @throws Exception
     */
    @Test
    void testRegexpForMatrixChildAndRetriggerAllWithoutMatrixPart() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "value1", "value2", "value3")
        );
        p.setAxes(axes);

        // axis1=value1 and axis1=value3 fails, and outputs "I am (axis value)"
        p.getBuildersList().add(new MyBuilder("I am ${axis1}", "axis1 in ['value2']"));
        NaginatorPublisher naginator = new NaginatorPublisher(
                "I am value1",
                false,  // rerunIfUnstable
                false,  // retunMatrixPart
                true,   // checkRegexp
                1,      // maxSchedule
                new FixedDelay(0)
        );
        naginator.setRegexpForMatrixStrategy(RegexpForMatrixStrategy.TestChildrenRetriggerAll);
        p.getPublishersList().add(naginator);

        p.scheduleBuild2(0);
        j.waitUntilNoActivity();

        // build rescheduled.
        assertEquals(2, lastBuildNumber(p));

        // all axes are rescheduled
        MatrixBuild b = p.getLastBuild();
        assertNotNull(b);
        assertNotNull(b.getExactRun(new Combination(axes, "value1")));
        assertNotNull(b.getExactRun(new Combination(axes, "value2")));
        assertNotNull(b.getExactRun(new Combination(axes, "value3")));
    }

    /**
     * Tests the behavior when <code>regexpForMatrixStrategy</code> is <code>TestChildrenRetriggerAll</code>
     * and <code>retunMatrixPart</code> is <code>false</code>.
     *
     * @throws Exception
     */
    @Test
    void testRegexpForMatrixChildAndRetriggerDontMatch() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "value1", "value2", "value3")
        );
        p.setAxes(axes);

        // all axis fails, and outputs "I am (axis value)"
        p.getBuildersList().add(new MyBuilder("I am ${axis1}", Result.FAILURE));
        NaginatorPublisher naginator = new NaginatorPublisher(
                "thatcannotbe",
                false,  // rerunIfUnstable
                false,  // retunMatrixPart
                true,   // checkRegexp
                1,      // maxSchedule
                new FixedDelay(0)
        );
        naginator.setRegexpForMatrixStrategy(RegexpForMatrixStrategy.TestChildrenRetriggerAll);
        p.getPublishersList().add(naginator);

        p.scheduleBuild2(0);
        j.waitUntilNoActivity();

        // build is not rescheduled as no child matches the regexp.
        assertEquals(1, lastBuildNumber(p));
    }

    /**
     * Test the regexp configuration is preserved for a matrix project.
     *
     * @throws Exception
     */
    @Test
    void testConfigurationForRegexpOnMatrixProject() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);

        NaginatorPublisher naginator = new NaginatorPublisher(
                "Some regular expression",
                false,  // rerunIfUnstable
                true,   // retunMatrixPart
                true,   // checkRegexp
                1,      // maxSchedule
                new FixedDelay(0)
        );
        naginator.setRegexpForMatrixStrategy(RegexpForMatrixStrategy.TestChildrenRetriggerAll);
        naginator.setNoChildStrategy(NoChildStrategy.RerunEmpty);

        p.getPublishersList().add(naginator);

        j.configRoundtrip(p);

        j.assertEqualDataBoundBeans(naginator, p.getPublishersList().get(NaginatorPublisher.class));
        // assertEqualDataBoundBeans doesn't work for DataBoundSetter fields.
        assertEquals(
                naginator.getRegexpForMatrixStrategy(),
                p.getPublishersList().get(NaginatorPublisher.class).getRegexpForMatrixStrategy()
        );
        assertEquals(
                naginator.getNoChildStrategy(),
                p.getPublishersList().get(NaginatorPublisher.class).getNoChildStrategy()
        );
    }

    /**
     * Test the regexp configuration for a freestyle project.
     * <code>regexpForMatrixParent</code> is not preserved as not displayed.
     *
     * @throws Exception
     */
    @Test
    void testConfigurationForRegexpOnFreeStyleProject() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        NaginatorPublisher naginator = new NaginatorPublisher(
                "Some regular expression",
                false,  // rerunIfUnstable
                true,   // retunMatrixPart
                true,   // checkRegexp
                1,      // maxSchedule
                new FixedDelay(0)
        );
        p.getPublishersList().add(naginator);

        NaginatorPublisher expected = new NaginatorPublisher(
                naginator.getRegexpForRerun(),
                naginator.isRerunIfUnstable(),
                false,  // retunMatrixPart (not preserved)
                naginator.isCheckRegexp(),
                naginator.getMaxSchedule(),
                naginator.getDelay()
        );

        j.configRoundtrip(p);

        // don't care about regexpForMatrixStrategy and noChildStrategy
        j.assertEqualDataBoundBeans(expected, p.getPublishersList().get(NaginatorPublisher.class));
    }

    @Test
    void testRegexpTimeoutMsInSystemConfiguration() throws Exception {
        NaginatorPublisher.DescriptorImpl d = (NaginatorPublisher.DescriptorImpl)j.jenkins.getDescriptor(NaginatorPublisher.class);
        assertNotNull(d);
        d.setRegexpTimeoutMs(NaginatorPublisher.DEFAULT_REGEXP_TIMEOUT_MS);
        d.save();       // ensure the default value is saved to the file.

        // set to the memory, but not saved to the file.
        d.setRegexpTimeoutMs(1000);

        j.configRoundtrip();

        d.load();
        assertEquals(1000, d.getRegexpTimeoutMs());
    }

    @Test
    void testReadResolveFor1_15() throws Exception {
        // for the case regexpForMatrixParent and regexpForMatrixStrategy is not set
        NaginatorPublisher target = new NaginatorPublisher(
                "",     // regexpForRerun
                false,  // rerunIfUnstable
                true,   // rerunMatrixPart
                true,   // checkRegexp
                1,      // maxschedule
                new FixedDelay(0)
        );

        Field regexpForMatrixParent = target.getClass().getDeclaredField("regexpForMatrixParent");
        Field regexpForMatrixStrategy = target.getClass().getDeclaredField("regexpForMatrixStrategy");
        try {
            regexpForMatrixParent.setAccessible(true);
            regexpForMatrixStrategy.setAccessible(true);
            regexpForMatrixParent.set(target, null);
            regexpForMatrixStrategy.set(target, null);
        } finally {
            regexpForMatrixParent.setAccessible(false);
            regexpForMatrixStrategy.setAccessible(false);
        }

        assertEquals(
                RegexpForMatrixStrategy.TestParent,
                ((NaginatorPublisher)target.readResolve()).getRegexpForMatrixStrategy()
        );
    }

    @Test
    void testReadResolveFor1_16WithTestParent() throws Exception {
        // for the case regexpForMatrixParent and regexpForMatrixStrategy is not set
        NaginatorPublisher target = new NaginatorPublisher(
                "",     // regexpForRerun
                false,  // rerunIfUnstable
                true,   // rerunMatrixPart
                true,   // checkRegexp
                1,      // maxschedule
                new FixedDelay(0)
        );

        Field regexpForMatrixParent = target.getClass().getDeclaredField("regexpForMatrixParent");
        Field regexpForMatrixStrategy = target.getClass().getDeclaredField("regexpForMatrixStrategy");
        try {
            regexpForMatrixParent.setAccessible(true);
            regexpForMatrixStrategy.setAccessible(true);
            regexpForMatrixParent.set(target, true);
            regexpForMatrixStrategy.set(target, null);
        } finally {
            regexpForMatrixParent.setAccessible(false);
            regexpForMatrixStrategy.setAccessible(false);
        }

        assertEquals(
                RegexpForMatrixStrategy.TestParent,
                ((NaginatorPublisher)target.readResolve()).getRegexpForMatrixStrategy()
        );
    }

    @Test
    void testReadResolveFor1_16WithTestChild() throws Exception {
        // for the case regexpForMatrixParent and regexpForMatrixStrategy is not set
        NaginatorPublisher target = new NaginatorPublisher(
                "",     // regexpForRerun
                false,  // rerunIfUnstable
                true,   // rerunMatrixPart
                true,   // checkRegexp
                1,      // maxschedule
                new FixedDelay(0)
        );

        Field regexpForMatrixParent = target.getClass().getDeclaredField("regexpForMatrixParent");
        Field regexpForMatrixStrategy = target.getClass().getDeclaredField("regexpForMatrixStrategy");
        try {
            regexpForMatrixParent.setAccessible(true);
            regexpForMatrixStrategy.setAccessible(true);
            regexpForMatrixParent.set(target, false);
            regexpForMatrixStrategy.set(target, null);
        } finally {
            regexpForMatrixParent.setAccessible(false);
            regexpForMatrixStrategy.setAccessible(false);
        }

        assertEquals(
                RegexpForMatrixStrategy.TestChildrenRetriggerMatched,
                ((NaginatorPublisher)target.readResolve()).getRegexpForMatrixStrategy()
        );
    }
}
