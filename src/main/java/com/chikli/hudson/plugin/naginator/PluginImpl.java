package com.chikli.hudson.plugin.naginator;

import hudson.Plugin;
import hudson.tasks.BuildStep;

/**
 * Entry point of a plugin.
 *
 * <p>
 * There must be one {@link Plugin} class in each plugin.
 * See javadoc of {@link Plugin} for more about what can be done on this class.
 *
 * @author Nayan Hajratwala <nayan@chikli.com>
 * @plugin
 */
public class PluginImpl extends Plugin {
    public void start() throws Exception {
        BuildStep.PUBLISHERS.add(NaginatorPublisher.DESCRIPTOR);
    }
}
