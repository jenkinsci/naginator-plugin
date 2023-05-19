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

import edu.umd.cs.findbugs.annotations.NonNull;
import org.jvnet.localizer.Localizable;

/**
 * The strategy for no children to rerun.
 * 
 * @since 1.17
 */
public enum NoChildStrategy {
    RerunWhole(Messages._NoChildStrategy_RerunWhole_DisplayName()),
    RerunEmpty(Messages._NoChildStrategy_RerunEmpty_DisplayName()),
    DontRun(Messages._NoChildStrategy_DontRerun_DisplayName()),
    ;
    private final Localizable displayName;
    
    private NoChildStrategy(Localizable displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName.toString();
    }
    
    @NonNull
    public static NoChildStrategy getDefault() {
        return RerunWhole;
    }
}
