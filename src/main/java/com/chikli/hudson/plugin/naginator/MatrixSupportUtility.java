/*
 * The MIT License
 *
 * Copyright (c) 2017 IKEDA Yasuyuki
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

import javax.annotation.Nonnull;

import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixRun;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.Jenkins;

/**
 * Utility to support matrix-project plugin.
 *
 * @since 1.18.0
 */
public class MatrixSupportUtility {
    /**
     * @return true if matrix-project-plugin is installed.
     */
    public static boolean isMatrixInstalled() {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return false;
        }
        return (jenkins.getPlugin("matrix-project") != null);
    }

    /**
     * @param run {@link Run} to test.
     * @return true if {@code run} is a {@link MatrixBuild}
     */
    public static boolean isMatrixBuild(@Nonnull Run<?, ?> run) {
        if (!isMatrixInstalled()) {
            return false;
        }
        return (run instanceof MatrixBuild);
    }

    /**
     * @param run {@link Run} to test.
     * @return true if {@code run} is a {@link MatrixRun}
     */
    public static boolean isMatrixRun(@Nonnull Run<?, ?> run) {
        if (!isMatrixInstalled()) {
            return false;
        }
        return (run instanceof MatrixRun);
    }

    /**
     * @param job {@link Job} to test.
     * @return true if {@code job} is a {@link MatrixProject}
     */
    public static boolean isMatrixProject(@Nonnull Job<?, ?> job) {
        if (!isMatrixInstalled()) {
            return false;
        }
        return (job instanceof MatrixProject);
    }
}
