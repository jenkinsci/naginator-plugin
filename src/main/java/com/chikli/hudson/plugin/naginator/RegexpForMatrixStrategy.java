/*
 * The MIT License
 * 
 * Copyright (c) 2016 IKEDA Yasuyuki
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

import org.jvnet.localizer.Localizable;

/**
 * How to apply regexp to matrix builds.
 */
public enum RegexpForMatrixStrategy {
    TestChildrenRetriggerAll(Messages._RegexpForMatrixStrategy_TestChildrenRetriggerAll()),
    TestChildrenRetriggerMatched(Messages._RegexpForMatrixStrategy_TestChildrenRetriggerMatched()),
    TestParent(Messages._RegexpForMatrixStrategy_TestParent()),
    ;
    private final Localizable displayName;
    
    private RegexpForMatrixStrategy(Localizable displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName.toString();
    }
    
    @Nonnull
    public static RegexpForMatrixStrategy getDefault() {
        return TestChildrenRetriggerAll;
    }
}
