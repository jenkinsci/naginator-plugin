package com.chikli.hudson.plugin.naginator;

import hudson.Extension;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.listeners.MatrixBuildListener;


/**
 *
 * @author galunto
 */
@Extension(optional=true)
public class NaginatorMatrixBuildListner extends MatrixBuildListener {
    
    /*
     * Used to filter the matrix parts which finished with success.
    */
    public boolean doBuildConfiguration(MatrixBuild mb, MatrixConfiguration mc) {
        NaginatorMatrixAction nma = mb.getAction(NaginatorMatrixAction.class);
        
        if (nma != null) {
            return nma.isCombinationNeedsRerun(mc.getCombination());
        }
        
        return true;
    }
}
