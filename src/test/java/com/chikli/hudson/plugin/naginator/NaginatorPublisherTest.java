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

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.chikli.hudson.plugin.naginator.testutils.MyBuilder;

import hudson.AbortException;
import hudson.Launcher;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.Combination;
import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.Builder;

/**
 * Tests for {@link NaginatorPublisher}.
 * Many tests for {@link NaginatorPublisher} are also in {@link NaginatorListenerTest}
 */
public class NaginatorPublisherTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    
    private static class FailSpecificAxisBuilder extends Builder {
        private final String combinationFilterToFail;
        
        public FailSpecificAxisBuilder(String combinationFilterToFail) {
            this.combinationFilterToFail = combinationFilterToFail;
        }
        
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            if (!(build instanceof MatrixRun)) {
                throw new AbortException("only applicable for MatrixRun");
            }
            
            MatrixRun run = (MatrixRun) build;
            
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
    public void testMatrixBuildWithoutRerunMatrixPart() throws Exception {
        final int maxSchedule = 2;
        
        MatrixProject p = j.createMatrixProject();
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
        
        assertEquals(maxSchedule + 1, p.getLastBuild().number);
        
        // (1, 1), (1, 2), (2, 1), (2, 2) are rescheduled.
        MatrixBuild b = p.getLastBuild();
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
    public void testMatrixBuildWithRerunMatrixPart() throws Exception {
        final int maxSchedule = 2;
        
        MatrixProject p = j.createMatrixProject();
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
        
        assertEquals(maxSchedule + 1, p.getLastBuild().number);
        
        // (1, 2), (2, 1) are rescheduled.
        MatrixBuild b = p.getLastBuild();
        assertNull(b.getExactRun(new Combination(axes, "1", "1")));
        assertNotNull(b.getExactRun(new Combination(axes, "1", "2")));
        assertNotNull(b.getExactRun(new Combination(axes, "2", "1")));
        assertNull(b.getExactRun(new Combination(axes, "2", "2")));
    }
    
    /**
     * Tests the behavior when <code>regexpForMatrixParent</code> is <code>true</code>
     * and the log match the regular expression.
     * 
     * @throws Exception
     */
    @Test
    public void testRegexpForMatrixParentMatches() throws Exception {
        MatrixProject p = j.createMatrixProject();
        AxisList axes = new AxisList(
                new Axis("axis1", "value1", "value2", "value3")
        );
        p.setAxes(axes);
        
        // only axis1=value2 fails.
        p.getBuildersList().add(new FailSpecificAxisBuilder("axis1 == 'value2'"));
        p.getPublishersList().add(new NaginatorPublisher(
                "value2 completed with result FAILURE",
                false,  // rerunIfUnstable
                true,   // retunMatrixPart
                true,   // checkRegexp
                true,   // regexpForMatrixParent
                1,      // maxSchedule
                new FixedDelay(0)
        ));
        
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();
        
        // build rescheduled.
        assertEquals(2, p.getLastBuild().number);
    }
    
    /**
     * Tests the behavior when <code>regexpForMatrixParent</code> is <code>true</code>
     * but the log doesn't match the regular expression.
     * 
     * @throws Exception
     */
    @Test
    public void testRegexpForMatrixParentNotMatches() throws Exception {
        MatrixProject p = j.createMatrixProject();
        AxisList axes = new AxisList(
                new Axis("axis1", "value1", "value2", "value3")
        );
        p.setAxes(axes);
        
        // only axis1=value3 fails.
        p.getBuildersList().add(new FailSpecificAxisBuilder("axis1 == 'value3'"));
        p.getPublishersList().add(new NaginatorPublisher(
                "value2 completed with result FAILURE",
                false,  // rerunIfUnstable
                true,   // retunMatrixPart
                true,   // checkRegexp
                true,   // regexpForMatrixParent
                1,      // maxSchedule
                new FixedDelay(0)
        ));
        
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();
        
        // build rescheduled.
        assertEquals(1, p.getLastBuild().number);
    }
    
    
    /**
     * Tests the behavior when <code>regexpForMatrixParent</code> is <code>false</code>.
     * 
     * @throws Exception
     */
    @Test
    public void testRegexpForMatrixChild() throws Exception {
        MatrixProject p = j.createMatrixProject();
        AxisList axes = new AxisList(
                new Axis("axis1", "value1", "value2", "value3")
        );
        p.setAxes(axes);
        
        // all axis fails, and outputs "I am (axis value)"
        p.getBuildersList().add(new MyBuilder("I am ${axis1}", Result.FAILURE));
        p.getPublishersList().add(new NaginatorPublisher(
                "I am value[13]",
                false,  // rerunIfUnstable
                true,   // retunMatrixPart
                true,   // checkRegexp
                false,  // regexpForMatrixParent
                1,      // maxSchedule
                new FixedDelay(0)
        ));
        
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();
        
        // build rescheduled.
        assertEquals(2, p.getLastBuild().number);
        
        // only axis1=value1, axis1=value3 is rescheduled for the regular expression.
        MatrixBuild b = p.getLastBuild();
        assertNotNull(b.getExactRun(new Combination(axes, "value1")));
        assertNull(b.getExactRun(new Combination(axes, "value2")));
        assertNotNull(b.getExactRun(new Combination(axes, "value3")));
    }
    
    /**
     * Test the regexp configuration is preserved for a matrix project.
     * 
     * @throws Exception
     */
    @Test
    public void testConfigurationForRegexpOnMatrixProject() throws Exception {
        MatrixProject p = j.createMatrixProject();
        
        NaginatorPublisher naginator = new NaginatorPublisher(
                "Some regular expression",
                false,  // rerunIfUnstable
                true,   // retunMatrixPart
                true,   // checkRegexp
                true,   // regexpForMatrixParent
                1,      // maxSchedule
                new FixedDelay(0)
        );
        
        p.getPublishersList().add(naginator);
        
        j.configRoundtrip(p);
        
        j.assertEqualDataBoundBeans(naginator, p.getPublishersList().get(NaginatorPublisher.class));
    }
    
    
    /**
     * Test the regexp configuration for a freestyle project.
     * <code>regexpForMatrixParent</code> is not preserved as not displayed.
     * 
     * @throws Exception
     */
    @Test
    public void testConfigurationForRegexpOnFreeStyleProject() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        NaginatorPublisher naginator = new NaginatorPublisher(
                "Some regular expression",
                false,  // rerunIfUnstable
                true,   // retunMatrixPart
                true,   // checkRegexp
                true,   // regexpForMatrixParent
                1,      // maxSchedule
                new FixedDelay(0)
        );
        NaginatorPublisher expected = new NaginatorPublisher(
                naginator.getRegexpForRerun(),
                naginator.isRerunIfUnstable(),
                naginator.isRerunMatrixPart(),
                naginator.isCheckRegexp(),
                false,  // regexpForMatrixParent (not preserved)
                naginator.getMaxSchedule(),
                naginator.getDelay()
        );
        
        p.getPublishersList().add(naginator);
        
        j.configRoundtrip(p);
        
        assertFalse(p.getPublishersList().get(NaginatorPublisher.class).isRegexpForMatrixParent());
        j.assertEqualDataBoundBeans(expected, p.getPublishersList().get(NaginatorPublisher.class));
    }
}
