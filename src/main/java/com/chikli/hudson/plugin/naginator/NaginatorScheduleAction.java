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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.matrix.MatrixRun;
import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * {@link Action} to mark a build to be rescheduled by {@link NaginatorListener}.
 * Be aware that you have to add this action to the parent build
 * if the build is the child of another build (e.g. multi-configuration projects).
 */
public class NaginatorScheduleAction extends InvisibleAction {
    private final int maxSchedule;
    private final ScheduleDelay delay;
    private final boolean rerunMatrixPart;
    
    /**
     * Should always reschedule the build.
     */
    public NaginatorScheduleAction() {
        this(0);
    }
    
    /**
     * Should reschedule the build for specified times.
     * 
     * @param maxSchedule max times to reschedule the build. Less or equal to 0 indicates "always".
     */
    public NaginatorScheduleAction(int maxSchedule) {
        this(maxSchedule, null, false);
    }
    
    /**
     * Should reschedule the build for specified times.
     * 
     * @param maxSchedule max times to reschedule the build. Less or equal to 0 indicates "always".
     * @param delay A scheduling policy to trigger a new build.
     * @param rerunMatrixPart tests matrix child builds and triggers only failed parts.
     */
    public NaginatorScheduleAction(int maxSchedule, @CheckForNull ScheduleDelay delay, boolean rerunMatrixPart) {
        this.maxSchedule = maxSchedule;
        this.delay = (delay != null) ? delay : new ProgressiveDelay(5 * 60, 3 * 60 * 60);
        this.rerunMatrixPart = rerunMatrixPart;
    }
    
    /**
     * The max times to reschedule the build.
     * Less or equal to 0 indicates "always".
     * 
     * @return the max times to reschedule the build.
     */
    public int getMaxSchedule() {
        return maxSchedule;
    }
    
    /**
     * @return A scheduling policy to trigger a new build
     */
    @Nonnull
    public ScheduleDelay getDelay() {
        return delay;
    }
    
    /**
     * @return whether to test each child builds to reschedule for multi-configuration builds.
     */
    public boolean isRerunMatrixPart() {
        return rerunMatrixPart;
    }
    
    /**
     * Tests whether {@link NaginatorListener} should reschedule the build.
     * You can override this method to reschedule the build conditionally.
     * <code>retryCount</code> is passed with 0 when this is the first time
     * to reschedule the build.
     * 
     * @param run a build to test. never be a {@link MatrixRun}
     * @param listener The listener for this build. This can be used to produce log messages, for example, which becomes a part of the "console output" of this build. But when this method runs, the build is considered completed, so its status cannot be changed anymore.
     * @param retryCount the count the build is rescheduled.
     * @return whether to reschedule the build.
     */
    public boolean shouldSchedule(@Nonnull Run<?, ?> run, @Nonnull TaskListener listener, int retryCount) {
        return getMaxSchedule() <= 0 || retryCount < getMaxSchedule();
    }
    
    /**
     * A test for each child builds of multi-configuration builds.
     * You can filter child builds to reschedule.
     * 
     * @param run
     * @return
     */
    public boolean shouldScheduleForMatrixRun(@Nonnull MatrixRun run, @Nonnull TaskListener listener) {
        return true;
    }
}
