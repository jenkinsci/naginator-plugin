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

import hudson.AbortException;
import hudson.Launcher;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.Combination;
import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
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
}
