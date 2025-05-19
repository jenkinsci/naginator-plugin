package com.chikli.hudson.plugin.naginator;

import hudson.model.Cause;
import hudson.model.Cause.UserIdCause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@WithJenkins
class NaginatorRetryActionTest {

    private static JenkinsRule j;

    private User user;

    private FreeStyleProject project;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        j = rule;
    }

    @BeforeEach
    void setUp() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        user = User.getById("naginator", true);
        project = j.createFreeStyleProject();
        project.getBuildersList().add(new FailureBuilder());
        project.getPublishersList().add(new NaginatorPublisher(
                "", // regexForRerun
                true,       // rerunIfUnstable
                false,      // rerunMatrixPart
                false,      // checkRegexp
                2,          // maxSchedule
                new FixedDelay(0)   // delay
        ));
        try (ACLContext ignored = ACL.as(user)) {
            project.scheduleBuild2(0, new UserIdCause());
        }
        j.waitUntilNoActivity();
    }

    @Issue("JENKINS-59222")
    @Test
    void testRetryUserCause() throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            User retryUser = User.getById("retry", true);

            FreeStyleBuild b = project.getBuildByNumber(3);
            try (ACLContext ignored = ACL.as(retryUser)) {
                NaginatorRetryAction.scheduleBuild2(b, 0,
                        new NaginatorAction(b, NaginatorListener.calculateRetryCount(b), 0),
                        true);
            }
            j.waitUntilNoActivity();
            b = project.getBuildByNumber(4);
            UserIdCause cause = b.getCause(UserIdCause.class);
            assertThat(cause, is(notNullValue()));
            assertThat(cause.getUserId(), is(retryUser.getId()));
        }
    }

    @Test
    void testUserIsKept() {
        FreeStyleBuild b = project.getBuildByNumber(3);
        UserIdCause cause = b.getCause(UserIdCause.class);
        assertThat(cause, is(notNullValue()));
        assertThat(cause.getUserId(), is(user.getId()));
    }

    @Test
    void testNaginatorCauseAppearsOnlyOnce() {
        FreeStyleBuild b = project.getBuildByNumber(3);
        List<Cause> causes = b.getCauses();
        long countOfNaginatorCause = causes.stream().filter(t -> t instanceof NaginatorCause).count();
        assertThat(countOfNaginatorCause, is(1L));
    }
}
