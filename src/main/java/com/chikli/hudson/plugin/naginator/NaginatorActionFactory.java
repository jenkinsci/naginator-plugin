package com.chikli.hudson.plugin.naginator;

import hudson.Extension;
import hudson.model.*;

import java.util.Collection;
import java.util.Collections;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension
public class NaginatorActionFactory extends TransientBuildActionFactory {

    @SuppressWarnings("rawtypes")
    @Override
    public Collection<? extends Action> createFor(Run target) {
        Result result = target.getResult();
        if (result != null && result.isWorseThan(Result.SUCCESS)) {
            NaginatorOptOutProperty p = (NaginatorOptOutProperty) target.getParent().getProperty(NaginatorOptOutProperty.class);
            if (p == null || !p.isOptOut()) return Collections.singleton(new NaginatorRetryAction());
        }
        return Collections.emptyList();
    }
}
