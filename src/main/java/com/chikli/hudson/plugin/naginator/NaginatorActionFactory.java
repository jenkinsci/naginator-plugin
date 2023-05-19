package com.chikli.hudson.plugin.naginator;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Result;
import jenkins.model.TransientActionFactory;

import java.util.Collection;
import java.util.Collections;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension
public class NaginatorActionFactory extends TransientActionFactory<AbstractBuild> {

    @Override
    public Class<AbstractBuild> type() {
        return AbstractBuild.class;
    }

    @Override
    @NonNull
    public Collection<? extends Action> createFor(@NonNull AbstractBuild target) {
        Result result = target.getResult();
        if (result != null && result.isWorseThan(Result.SUCCESS)) {
            NaginatorOptOutProperty p = (NaginatorOptOutProperty) target.getParent().getProperty(NaginatorOptOutProperty.class);
            if (p == null || !p.isOptOut()) return Collections.singleton(new NaginatorRetryAction());
        }
        return Collections.emptyList();
    }
}
