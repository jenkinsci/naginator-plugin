package com.chikli.hudson.plugin.naginator;

import com.chikli.hudson.plugin.naginator.testutils.MyBuilder;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.Combination;
import hudson.matrix.MatrixProject;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.util.Arrays;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.ToolInstallations;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.chikli.hudson.plugin.naginator.testutils.TestSupport.lastBuildNumber;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NaginatorListenerTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void testSuccessNoRebuild() throws Exception {
        assertFalse(isScheduledForRetry("build log", Result.SUCCESS, "foo", false, false));
    }

    @Test
    public void testUnstableNoRebuild() throws Exception {
        assertFalse(isScheduledForRetry("build log", Result.SUCCESS, "foo", false, false));
    }

    @Test
    public void testUnstableWithRebuild() throws Exception {
        assertTrue(isScheduledForRetry("build log", Result.UNSTABLE, "foo", true, false));
    }

    @Test
    public void testFailureWithRebuild() throws Exception {
        assertTrue(isScheduledForRetry("build log", Result.FAILURE, "foo", false, false));
    }

    @Test
    public void testFailureWithUnstableRebuild() throws Exception {
        assertTrue(isScheduledForRetry("build log", Result.FAILURE, "foo", true, false));
    }

    @Test
    public void testFailureWithoutRebuildRegexp() throws Exception {
        assertFalse(isScheduledForRetry("build log", Result.FAILURE, "foo", false, true));
    }

    @Test
    public void testFailureWithRebuildRegexp() throws Exception {
        assertTrue(isScheduledForRetry("build log foo", Result.FAILURE, "foo", false, true));
    }

    @Test
    public void testUnstableWithoutRebuildRegexp() throws Exception {
        assertFalse(isScheduledForRetry("build log", Result.UNSTABLE, "foo", true, true));
    }

    @Test
    public void testUnstableWithRebuildRegexp() throws Exception {
        assertTrue(isScheduledForRetry("build log foo", Result.UNSTABLE, "foo", true, true));
    }

    @Test
    public void testWithBuildWrapper() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new MyBuilder("foo", Result.SUCCESS, 1000));
        NaginatorPublisher nag = new NaginatorPublisher("foo", false, false, false, 10, new FixedDelay(0));
        project.getPublishersList().add(nag);
        BuildWrapper failTheBuild = new FailTheBuild();
        project.getBuildWrappersList().add(failTheBuild);

        assertTrue(isScheduledForRetry(project));
    }

    /**
     * A -> B
     *
     * A triggers B if successful.
     * B will be rebuild 2 times if it fails and it will.
     *
     * Test will check that B:s rebuild attempts will also contain the UpstreamCause from the original B:s build
     *
     * @throws Exception
     */
    @Test
    public void testRetainCauses() throws Exception {
        FreeStyleProject a = j.createFreeStyleProject("a");
        FreeStyleProject b = j.createFreeStyleProject("b");
        a.getPublishersList().add(new BuildTrigger("b", Result.SUCCESS));
        NaginatorPublisher nag = new NaginatorPublisher("", false, false, false, 2, new FixedDelay(1));

        b.getPublishersList().add(nag);

        BuildWrapper failTheBuild = new FailTheBuild();
        b.getBuildWrappersList().add(failTheBuild);
        j.jenkins.rebuildDependencyGraph();

        j.buildAndAssertSuccess(a);
        j.waitUntilNoActivity();
        assertNotNull(a.getLastBuild());
        assertNotNull(b.getLastBuild());
        assertEquals(1, a.getLastBuild().getNumber());
        assertEquals(3, b.getLastBuild().getNumber());
        assertEquals(Result.FAILURE, b.getLastBuild().getResult());
        assertEquals(2, b.getBuildByNumber(2).getAction(CauseAction.class).getCauses().size());
        assertEquals(2, b.getBuildByNumber(3).getAction(CauseAction.class).getCauses().size());
    }


    private boolean isScheduledForRetry(String buildLog, Result result, String regexpForRerun,
                                    boolean rerunIfUnstable, boolean checkRegexp) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new MyBuilder(buildLog, result));
        Publisher nag = new NaginatorPublisher(regexpForRerun, rerunIfUnstable, false, checkRegexp, 10, new FixedDelay(0));
        project.getPublishersList().add(nag);

        return isScheduledForRetry(project);
    }

    private boolean isScheduledForRetry(FreeStyleProject project) throws Exception {
        project.scheduleBuild2(0).get();
        j.waitUntilNoActivity();

        return lastBuildNumber(project) > 1;
    }

    private static final class FailTheBuild extends BuildWrapper {
        @Override
        public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            return new Environment() {
                @Override
                public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                    build.setResult(Result.FAILURE);
                    return true;
                }
            };
        }

        @Extension
        public static class DescriptorImpl extends BuildWrapperDescriptor {

            public boolean isApplicable(AbstractProject<?, ?> item) {
                return true;
            }

            public String getDisplayName() {
                return null;
            }
        }
    }

    @Test
    @Bug(17626)
    public void testCountScheduleIndependently() throws Exception {
        // Running a two sequence of builds
        // with parameter PARAM=A and PARAM=B.
        // Each of them should be rescheduled
        // 2 times.
        
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("PARAM", "")
        ));
        p.getBuildersList().add(new SleepBuilder(100));
        p.getBuildersList().add(new FailureBuilder());
        p.getPublishersList().add(new NaginatorPublisher(
                "",                     // regexpForRerun
                false,                  // rerunIfUnstable
                false,                  // rerunMatrixPart
                false,                  // checkRegexp
                2,                      // maxSchedule
                new FixedDelay(0)       // delay
        ));
        
        p.scheduleBuild2(
                0,
                new Cause.UserIdCause(),
                new ParametersAction(
                    new StringParameterValue("PARAM", "A")
                )
        );
        p.scheduleBuild2(
                0,
                new Cause.UserIdCause(),
                new ParametersAction(
                    new StringParameterValue("PARAM", "B")
                )
        );
        
        j.waitUntilNoActivity();
        
        assertEquals(3, Collections2.filter(
                p.getBuilds(),
                new Predicate<FreeStyleBuild>() {
                    public boolean apply(FreeStyleBuild b) {
                        try {
                            return "A".equals(b.getEnvironment(TaskListener.NULL).get("PARAM"));
                        } catch (IOException e) {
                            return false;
                        } catch (InterruptedException e) {
                            return false;
                        }
                    }
                }
        ).size());
        assertEquals(3, Collections2.filter(
                p.getBuilds(),
                new Predicate<FreeStyleBuild>() {
                    public boolean apply(FreeStyleBuild b) {
                        try {
                            return "B".equals(b.getEnvironment(TaskListener.NULL).get("PARAM"));
                        } catch (IOException e) {
                            return false;
                        } catch (InterruptedException e) {
                            return false;
                        }
                    }
                }
        ).size());
    }

    @Test
    @Bug(24903)
    public void testCatastorophicRegularExpression() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new MyBuilder("0000000000000000000000000000000000000000000000000000", Result.FAILURE));
        p.getPublishersList().add(new NaginatorPublisher(
                "(0*)*NOSUCHSTRING",               // regexpForRerun
                false,                  // rerunIfUnstable
                false,                  // rerunMatrixPart
                true,                   // checkRegexp
                1,                      // maxSchedule
                new FixedDelay(0)       // delay
        ));

        NaginatorPublisher.DescriptorImpl descriptor = (NaginatorPublisher.DescriptorImpl) j.jenkins.getDescriptor(NaginatorPublisher.class);
        assertNotNull(descriptor);
        descriptor.setRegexpTimeoutMs(1000);
        
        p.scheduleBuild2(0);
        j.waitUntilNoActivityUpTo(10 * 1000);
    }
    
    
    public static class VariableRecordBuilder extends Builder {
        private final String name;
        private final Map<String, String> recorded = new HashMap<>();
        
        public VariableRecordBuilder(@NonNull String name) {
            this.name = name;
        }
        
        private String getIdForBuild(@NonNull Run<?, ?> r) {
            return String.format("%s-%s", r.getParent().getFullName(), r.getId());
        }
        
        @CheckForNull
        public String getRecordedValue(@NonNull Run<?, ?> r) {
            return recorded.get(getIdForBuild(r));
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            recorded.put(getIdForBuild(build), build.getEnvironment(listener).get(name));
            return true;
        }
    }

    @Test
    public void testVariable() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        VariableRecordBuilder countRecorder = new VariableRecordBuilder("NAGINATOR_COUNT");
        VariableRecordBuilder maxCountRecorder = new VariableRecordBuilder("NAGINATOR_MAXCOUNT");
        VariableRecordBuilder buildNumberRecorder = new VariableRecordBuilder("NAGINATOR_BUILD_NUMBER");
        p.getBuildersList().add(countRecorder);
        p.getBuildersList().add(maxCountRecorder);
        p.getBuildersList().add(buildNumberRecorder);
        p.getBuildersList().add(new FailureBuilder());
        p.getPublishersList().add(new NaginatorPublisher(
                "",     // regexpForRerun
                false,  // rerunIfUnstable
                false,  // rerunMatrixPart
                false,  // checkRegexp
                false,  // regexpForMatrixParent
                2,      // maxSchedule
                new FixedDelay(0) // delay
        ));
        
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();
        
        // There should be 3 builds
        assertEquals(3, lastBuildNumber(p));
        
        // for the first build (not a retrying build)
        assertNull(countRecorder.getRecordedValue(p.getBuildByNumber(1)));
        assertNull(maxCountRecorder.getRecordedValue(p.getBuildByNumber(1)));
        assertNull(buildNumberRecorder.getRecordedValue(p.getBuildByNumber(1)));
        
        // for the first retry
        assertEquals("1", countRecorder.getRecordedValue(p.getBuildByNumber(2)));
        assertEquals("2", maxCountRecorder.getRecordedValue(p.getBuildByNumber(2)));
        assertEquals("1", buildNumberRecorder.getRecordedValue(p.getBuildByNumber(2)));
        
        // for the second retry
        assertEquals("2", countRecorder.getRecordedValue(p.getBuildByNumber(3)));
        assertEquals("2", maxCountRecorder.getRecordedValue(p.getBuildByNumber(3)));
        assertEquals("2", buildNumberRecorder.getRecordedValue(p.getBuildByNumber(3)));
    }

    @Test
    public void testVariableForMatrixBuild() throws Exception {
        MatrixProject p = j.jenkins.createProject(MatrixProject.class, createUniqueProjectName());
        AxisList axisList = new AxisList(new Axis("axis1", "value1", "value2"));
        p.setAxes(axisList);
        VariableRecordBuilder countRecorder = new VariableRecordBuilder("NAGINATOR_COUNT");
        VariableRecordBuilder maxCountRecorder = new VariableRecordBuilder("NAGINATOR_MAXCOUNT");
        VariableRecordBuilder buildNumberRecorder = new VariableRecordBuilder("NAGINATOR_BUILD_NUMBER");
        p.getBuildersList().add(countRecorder);
        p.getBuildersList().add(maxCountRecorder);
        p.getBuildersList().add(buildNumberRecorder);
        p.getBuildersList().add(new FailureBuilder());
        p.getPublishersList().add(new NaginatorPublisher(
                "",     // regexpForRerun
                false,  // rerunIfUnstable
                false,  // rerunMatrixPart
                false,  // checkRegexp
                false,  // regexpForMatrixParent
                2,      // maxSchedule
                new FixedDelay(0) // delay
        ));
        
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();
        
        // There should be 3 builds
        assertEquals(3, lastBuildNumber(p));
        
        // for the first build (not a retrying build)
        assertNull(countRecorder.getRecordedValue(p.getBuildByNumber(1).getExactRun(new Combination(axisList, "value1"))));
        assertNull(maxCountRecorder.getRecordedValue(p.getBuildByNumber(1).getExactRun(new Combination(axisList, "value1"))));
        assertNull(buildNumberRecorder.getRecordedValue(p.getBuildByNumber(1).getExactRun(new Combination(axisList, "value1"))));
        assertNull(countRecorder.getRecordedValue(p.getBuildByNumber(1).getExactRun(new Combination(axisList, "value2"))));
        assertNull(maxCountRecorder.getRecordedValue(p.getBuildByNumber(1).getExactRun(new Combination(axisList, "value2"))));
        assertNull(buildNumberRecorder.getRecordedValue(p.getBuildByNumber(1).getExactRun(new Combination(axisList, "value2"))));
        
        // for the first retry
        assertEquals("1", countRecorder.getRecordedValue(p.getBuildByNumber(2).getExactRun(new Combination(axisList, "value1"))));
        assertEquals("2", maxCountRecorder.getRecordedValue(p.getBuildByNumber(2).getExactRun(new Combination(axisList, "value1"))));
        assertEquals("1", buildNumberRecorder.getRecordedValue(p.getBuildByNumber(2).getExactRun(new Combination(axisList, "value1"))));
        assertEquals("1", countRecorder.getRecordedValue(p.getBuildByNumber(2).getExactRun(new Combination(axisList, "value2"))));
        assertEquals("2", maxCountRecorder.getRecordedValue(p.getBuildByNumber(2).getExactRun(new Combination(axisList, "value2"))));
        assertEquals("1", buildNumberRecorder.getRecordedValue(p.getBuildByNumber(2).getExactRun(new Combination(axisList, "value2"))));
        
        // for the second retry
        assertEquals("2", countRecorder.getRecordedValue(p.getBuildByNumber(3).getExactRun(new Combination(axisList, "value1"))));
        assertEquals("2", maxCountRecorder.getRecordedValue(p.getBuildByNumber(3).getExactRun(new Combination(axisList, "value1"))));
        assertEquals("2", buildNumberRecorder.getRecordedValue(p.getBuildByNumber(3).getExactRun(new Combination(axisList, "value1"))));
        assertEquals("2", countRecorder.getRecordedValue(p.getBuildByNumber(3).getExactRun(new Combination(axisList, "value2"))));
        assertEquals("2", maxCountRecorder.getRecordedValue(p.getBuildByNumber(3).getExactRun(new Combination(axisList, "value2"))));
        assertEquals("2", buildNumberRecorder.getRecordedValue(p.getBuildByNumber(3).getExactRun(new Combination(axisList, "value2"))));
    }
    
    public static class PrepareSingleFileProperty extends JobProperty<Job<?,?>> {
        private final String filename;
        private final byte[] contents;
        
        public PrepareSingleFileProperty(String filename, byte[] contents) {
            this.filename = filename;
            this.contents = Arrays.copyOf(contents, contents.length);
        }
        
        @Override
        public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
            OutputStream os;
            try {
                FilePath workspace = build.getWorkspace();
                assert workspace != null;
                os = workspace.child(filename).write();
            } catch (IOException e) {
                e.printStackTrace(listener.getLogger());
                return false;
            } catch (InterruptedException e) {
                e.printStackTrace(listener.getLogger());
                return false;
            }
            try {
                os.write(contents);
            } catch (IOException e) {
                e.printStackTrace(listener.getLogger());
                return false;
            } finally {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace(listener.getLogger());
                }
            }
            return true;
        }
        
        @Extension
        public static class DescriptorImpl extends JobPropertyDescriptor {
            @Override
            public String getDisplayName() {
                return "PrepareSingleFileProperty";
            }
        }
    }

    @Test
    @Issue("JENKINS-34900")
    public void testMavenModuleSetWithoutNaginator() throws Exception {
        final String SIMPLE_POM = StringUtils.join(new String[]{
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">",
                "  <modelVersion>4.0.0</modelVersion>",
                "  <groupId>com.exmaple</groupId>",
                "  <artifactId>test</artifactId>",
                "  <version>1.0</version>",
                "  <packaging>jar</packaging>",
                "  <repositories>",
                "    <repository>",
                "      <id>central</id>",
                "      <url>https://repo.maven.apache.org/maven2</url>",
                "    </repository>",
                "  </repositories>",
                "  <pluginRepositories>",
                "    <pluginRepository>",
                "      <id>central</id>",
                "      <url>https://repo.maven.apache.org/maven2</url>",
                "    </pluginRepository>",
                "  </pluginRepositories>",
                "</project>"
        }, "\n");
        
        ToolInstallations.configureMaven35();
        MavenModuleSet p = j.createProject(MavenModuleSet.class, createUniqueProjectName());
        
        // as SingleFileSCM in jenkins-test-harness doesn't work with
        // Jenkins 1.554, use a simple custom JobProperty instead.
        // p.setScm(new SingleFileSCM("pom.xml", SIMPLE_POM));
        p.addProperty(new PrepareSingleFileProperty("pom.xml", SIMPLE_POM.getBytes(StandardCharsets.UTF_8)));
        p.setGoals("clean");
        
        // Run once to have the project read the module structure.
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        
        // This results MavenModuleBuild#getRootBuild() to be `null`.
        j.assertBuildStatusSuccess(p.getRootModule().scheduleBuild2(0));
    }

    private static String createUniqueProjectName() {
        return "test" + System.currentTimeMillis();
    }
}
