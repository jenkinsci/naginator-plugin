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
import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;

import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 */
public class NaginatorScheduleActionTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    
    public static class ScheduleActionBuilder extends Builder {
        private final NaginatorScheduleAction[] actions;
        
        public ScheduleActionBuilder(NaginatorScheduleAction... actions) {
            this.actions = actions;
        }
        
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            if (build instanceof MatrixRun) {
                MatrixBuild parent = ((MatrixRun) build).getParentBuild();
                if (parent.getAction(NaginatorScheduleAction.class) == null) {
                    for (NaginatorScheduleAction action : actions) {
                        parent.addAction(action);
                    }
                }
            } else {
                for (NaginatorScheduleAction action : actions) {
                    build.addAction(action);
                }
            }
            return true;
        }
    }
    
    private static class AlwaysFalseScheduleAction extends NaginatorScheduleAction {
        public AlwaysFalseScheduleAction(int maxSchedule, ScheduleDelay delay, boolean rerunMatrixPart) {
            super(maxSchedule, delay, rerunMatrixPart);
        }
        
        @Override
        public boolean shouldSchedule(Run<?, ?> run, TaskListener listener, int retryCount) {
            return false;
        }
    }
    
    private static class MatrixConfigurationScheduleAction extends NaginatorScheduleAction {
        private final String combinationFilter;
        private final NoChildStrategy noChildStrategy;
        
        public MatrixConfigurationScheduleAction(String combinationFilter, int maxSchedule, ScheduleDelay delay, boolean rerunMatrixPart, NoChildStrategy noChildStrategy) {
            super(maxSchedule, delay, rerunMatrixPart);
            this.combinationFilter = combinationFilter;
            this.noChildStrategy = noChildStrategy;
        }
        
        public MatrixConfigurationScheduleAction(String combinationFilter, int maxSchedule, ScheduleDelay delay, boolean rerunMatrixPart) {
            this(combinationFilter, maxSchedule, delay, rerunMatrixPart, NoChildStrategy.getDefault());
        }
        
        @Override
        public boolean shouldScheduleForMatrixRun(MatrixRun run, TaskListener listener) {
            return run.getParent().getCombination().evalGroovyExpression(
                    run.getParent().getParent().getAxes(),
                    combinationFilter
            );
        }
        
        @Override
        public NoChildStrategy getNoChildStrategy() {
            return noChildStrategy;
        }
    }
    
    /**
     * {@link NaginatorScheduleAction#shouldSchedule(Run, TaskListener, int)}
     * should be true only while <code>retryCount</code> is
     * under <code>maxSchedule</code>
     * 
     * @throws Exception
     */
    @Test
    public void testShouldReschedule() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        
        NaginatorScheduleAction target = new NaginatorScheduleAction(2);
        assertTrue(target.shouldSchedule(b, TaskListener.NULL, 0));
        assertTrue(target.shouldSchedule(b, TaskListener.NULL, 1));
        assertFalse(target.shouldSchedule(b, TaskListener.NULL, 2));
    }
        
    /**
     * {@link NaginatorScheduleAction#shouldSchedule(Run, TaskListener, int)}
     * should be true always true when <code>maxSchedule</code> is 0.
     * 
     * @throws Exception
     */
    @Test
    public void testShouldRescheduleWithMaxScheduleIs0() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        
        NaginatorScheduleAction target = new NaginatorScheduleAction();
        assertEquals(0, target.getMaxSchedule());
        assertTrue(target.shouldSchedule(b, TaskListener.NULL, 0));
        assertTrue(target.shouldSchedule(b, TaskListener.NULL, 100));
        assertTrue(target.shouldSchedule(b, TaskListener.NULL, 2000));
    }
        
    /**
     * {@link NaginatorScheduleAction#shouldSchedule(Run, TaskListener, int)}
     * should be true always true when <code>maxSchedule</code> is under 0.
     * This is not a correct usage but a fail-safe.
     * 
     * @throws Exception
     */
    @Test
    public void testShouldRescheduleWithMaxScheduleIsMinus() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        
        NaginatorScheduleAction target = new NaginatorScheduleAction(-1);
        assertTrue(target.shouldSchedule(b, TaskListener.NULL, 0));
        assertTrue(target.shouldSchedule(b, TaskListener.NULL, 100));
        assertTrue(target.shouldSchedule(b, TaskListener.NULL, 2000));
    }
    
    /**
     * {@link NaginatorPublisher} should not reschedule a build
     * without {@link NaginatorScheduleAction}.
     * 
     * @throws Exception
     */
    @Test
    public void testNoNaginatorScheduleAction() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new ScheduleActionBuilder());
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();
        
        // no reschedule.
        assertEquals(1, p.getLastBuild().number);
    }
    
    /**
     * {@link NaginatorScheduleAction} should have 
     * {@link NaginatorPublisher} reschedule the build
     * with times specified with <code>maxSchedule</code>
     * 
     * @throws Exception
     */
    @Test
    public void testReschedule() throws Exception {
        final int maxSchedule = 2;
        
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new ScheduleActionBuilder(
                new NaginatorScheduleAction(
                        maxSchedule,
                        new FixedDelay(0),
                        false
                )
        ));
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();
        
        assertEquals(maxSchedule + 1, p.getLastBuild().number);
    }
        
    /**
     * {@link NaginatorScheduleAction#shouldSchedule(Run, TaskListener, int)}
     * returning 0 should not have {@link NaginatorPublisher}
     * reschedule the build.
     * 
     * @throws Exception
     */
    @Test
    public void testNotRescheduledWithShouldRescheduleReturning0() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new ScheduleActionBuilder(
                new AlwaysFalseScheduleAction(
                        2,
                        new FixedDelay(0),
                        false
                )
        ));
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();
        
        // no reschedule.
        assertEquals(1, p.getLastBuild().number);
    }
        
    /**
     * When there are multiple {@link NaginatorScheduleAction}s,
     * {@link NaginatorPublisher} should reschedule the build
     * if any of them return true.
     * 
     * @throws Exception
     */
    @Test
    public void testMultipleNaginatorScheduleAction() throws Exception {
        final int maxSchedule = 2;
        
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new ScheduleActionBuilder(
                new AlwaysFalseScheduleAction(
                        maxSchedule,
                        new FixedDelay(0),
                        false
                ),
                new NaginatorScheduleAction(
                        maxSchedule,
                        new FixedDelay(0),
                        false
                )
        ));
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();
        
        assertEquals(maxSchedule + 1, p.getLastBuild().number);
    }
    
    /**
     * The default behavior of {@link NaginatorScheduleAction}
     * for multi-configuration projects is to run all children
     * even if <code>rerunMatrixPart</code> is set.
     * 
     * @throws Exception
     */
    @Test
    public void testMatrixWithRerunMatrixPartWithoutFiltering() throws Exception {
        final int maxSchedule = 2;
        
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "1", "2"),
                new Axis("axis2", "1", "2")
        );
        p.setAxes(axes);
        p.setCombinationFilter("!(axis1=='2' && axis2=='2')");
        p.getBuildersList().add(new ScheduleActionBuilder(
                new NaginatorScheduleAction(
                        maxSchedule,
                        new FixedDelay(0),
                        true
                )
        ));
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();
        
        assertEquals(maxSchedule + 1, p.getLastBuild().number);
        
        // (1, 1), (1, 2), (2, 1) are scheduled
        MatrixBuild b = p.getLastBuild();
        assertNotNull(b.getExactRun(new Combination(axes, "1", "1")));
        assertNotNull(b.getExactRun(new Combination(axes, "1", "2")));
        assertNotNull(b.getExactRun(new Combination(axes, "2", "1")));
        assertNull(b.getExactRun(new Combination(axes, "2", "2")));
    }
    
    /**
     * Overriding {@link NaginatorScheduleAction#shouldScheduleForMatrixRun(MatrixRun, TaskListener)}
     * allows filter children to reschedule.
     * 
     * @throws Exception
     */
    @Test
    public void testMatrixWithRerunMatrixPartWithFiltering() throws Exception {
        final int maxSchedule = 1;
        
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "1", "2"),
                new Axis("axis2", "1", "2")
        );
        p.setAxes(axes);
        p.setCombinationFilter("!(axis1=='2' && axis2=='2')");
        p.getBuildersList().add(new ScheduleActionBuilder(
                new MatrixConfigurationScheduleAction(
                        "(axis1=='1' && axis2=='2') || (axis1=='2' && axis2=='1')",
                        maxSchedule,
                        new FixedDelay(0),
                        true
                )
        ));
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();
        
        assertEquals(maxSchedule + 1, p.getLastBuild().number);
        
        // (1, 2), (2, 1) are scheduled
        MatrixBuild b = p.getLastBuild();
        assertNull(b.getExactRun(new Combination(axes, "1", "1")));
        assertNotNull(b.getExactRun(new Combination(axes, "1", "2")));
        assertNotNull(b.getExactRun(new Combination(axes, "2", "1")));
        assertNull(b.getExactRun(new Combination(axes, "2", "2")));
    }
    
    /**
     * Filtering out all children cause trigger all children.
     * 
     * @throws Exception
     */
    @Test
    public void testMatrixWithNoChildren() throws Exception {
        final int maxSchedule = 1;
        
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "1", "2"),
                new Axis("axis2", "1", "2")
        );
        p.setAxes(axes);
        p.setCombinationFilter("!(axis1=='2' && axis2=='2')");
        p.getBuildersList().add(new ScheduleActionBuilder(
                new MatrixConfigurationScheduleAction(
                        "false",
                        maxSchedule,
                        new FixedDelay(0),
                        true,
                        NoChildStrategy.RerunWhole
                )
        ));
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();
        
        assertEquals(maxSchedule + 1, p.getLastBuild().number);
        
        // (1, 1), (1, 2), (2, 1) are scheduled
        MatrixBuild b = p.getLastBuild();
        assertNotNull(b.getExactRun(new Combination(axes, "1", "1")));
        assertNotNull(b.getExactRun(new Combination(axes, "1", "2")));
        assertNotNull(b.getExactRun(new Combination(axes, "2", "1")));
        assertNull(b.getExactRun(new Combination(axes, "2", "2")));
    }
    
    /**
     * Filtering out all children with DontRerun strategy
     * cause trigger nothing.
     * 
     * @throws Exception
     */
    @Test
    public void testMatrixWithNoChildrenNoRerun() throws Exception {
        final int maxSchedule = 1;
        
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "1", "2"),
                new Axis("axis2", "1", "2")
        );
        p.setAxes(axes);
        p.setCombinationFilter("!(axis1=='2' && axis2=='2')");
        p.getBuildersList().add(new ScheduleActionBuilder(
                new MatrixConfigurationScheduleAction(
                        "false",
                        maxSchedule,
                        new FixedDelay(0),
                        true,
                        NoChildStrategy.DontRun
                )
        ));
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();
        
        assertEquals(1, p.getLastBuild().number);
    }
    
    /**
     * Filtering out all children cause trigger the matrix,
     * but no children.
     * 
     * @throws Exception
     */
    @Test
    public void testMatrixWithNoChildrenEmpty() throws Exception {
        final int maxSchedule = 1;
        
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "1", "2"),
                new Axis("axis2", "1", "2")
        );
        p.setAxes(axes);
        p.setCombinationFilter("!(axis1=='2' && axis2=='2')");
        p.getBuildersList().add(new ScheduleActionBuilder(
                new MatrixConfigurationScheduleAction(
                        "false",
                        maxSchedule,
                        new FixedDelay(0),
                        true,
                        NoChildStrategy.RerunEmpty
                )
        ));
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();
        
        assertEquals(maxSchedule + 1, p.getLastBuild().number);
        
        // No children are rescheduled
        MatrixBuild b = p.getLastBuild();
        assertNull(b.getExactRun(new Combination(axes, "1", "1")));
        assertNull(b.getExactRun(new Combination(axes, "1", "2")));
        assertNull(b.getExactRun(new Combination(axes, "2", "1")));
        assertNull(b.getExactRun(new Combination(axes, "2", "2")));
    }
    
    /**
     * Unsetting <code>rerunMatrixPart</code> triggers all children
     * even if overriding {@link NaginatorScheduleAction#shouldScheduleForMatrixRun(MatrixRun, TaskListener)}
     * 
     * @throws Exception
     */
    @Test
    public void testMatrixWithoutRerunMatrixPart() throws Exception {
        final int maxSchedule = 1;
        
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "1", "2"),
                new Axis("axis2", "1", "2")
        );
        p.setAxes(axes);
        p.setCombinationFilter("!(axis1=='2' && axis2=='2')");
        p.getBuildersList().add(new ScheduleActionBuilder(
                new MatrixConfigurationScheduleAction(
                        "(axis1=='1' && axis2=='2') || (axis1=='2' && axis2=='1')",
                        1,
                        new FixedDelay(0),
                        false
                )
        ));
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();
        
        assertEquals(maxSchedule + 1, p.getLastBuild().number);
        
        // (1, 1), (1, 2), (2, 1) are scheduled
        MatrixBuild b = p.getLastBuild();
        assertNotNull(b.getExactRun(new Combination(axes, "1", "1")));
        assertNotNull(b.getExactRun(new Combination(axes, "1", "2")));
        assertNotNull(b.getExactRun(new Combination(axes, "2", "1")));
        assertNull(b.getExactRun(new Combination(axes, "2", "2")));
    }
}
