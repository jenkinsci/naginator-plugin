package com.chikli.hudson.plugin.naginator;

import hudson.matrix.Combination;
import java.util.ArrayList;
import java.util.List;

/**
 * This is an extention for the NaginatorAction class which used to store the
 * combinations to rerun.
 * @author galunto
 */
public class NaginatorMatrixAction extends NaginatorAction {
    private List<Combination> combsToRerun;

    public NaginatorMatrixAction() {
        this.combsToRerun = new ArrayList<Combination>();
    }
    
    public void addCombinationToRerun(Combination combination) {
        this.combsToRerun.add(combination);
    }

    public List<Combination> getCombinationsToRerun() {
        return this.combsToRerun;
    }
    
    public boolean isCombinationNeedsRerun(Combination combination) {
        return this.combsToRerun.contains(combination);
    }
}
