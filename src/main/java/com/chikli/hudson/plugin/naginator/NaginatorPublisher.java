package com.chikli.hudson.plugin.naginator;

import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;

import javax.servlet.http.HttpServletRequest;

import org.kohsuke.stapler.StaplerRequest;

/**
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link NaginatorPublisher} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(Build, Launcher, BuildListener)} method
 * will be invoked. 
 *
 * @author Nayan Hajratwala <nayan@chikli.com>
 */
public class NaginatorPublisher extends Publisher {

    NaginatorPublisher() {
    }

    public boolean perform(Build build, Launcher launcher, BuildListener listener) {
        
        // If the build was successful, we don't need to Nag, so just return true
        if (build.getResult() == Result.SUCCESS) {
            return true;
        }

        // Schedule a new build.
        return build.getProject().scheduleBuild();
    }

    public Descriptor<Publisher> getDescriptor() {
        // see Descriptor javadoc for more about what a descriptor is.
        return DESCRIPTOR;
    }

    /**
     * Descriptor should be singleton.
     */
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
     * Descriptor for {@link NaginatorPublisher}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>views/hudson/plugins/naginator/NaginatorBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    public static final class DescriptorImpl extends Descriptor<Publisher> {

        DescriptorImpl() {
            super(NaginatorPublisher.class);
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Retry build after failure (Naginator)";
        }

        public boolean configure(HttpServletRequest req) throws FormException {
            save();
            return super.configure(req);
        }


        /**
         * Creates a new instance of {@link NaginatorPublisher} from a submitted form.
         */
        public NaginatorPublisher newInstance(StaplerRequest req) throws FormException {
            // see config.jelly and you'll find "hello_world.name" form entry.
            return new NaginatorPublisher();
        }
    }
}
