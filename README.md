# Naginator Plugin

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/naginator)](https://plugins.jenkins.io/naginator)
[![Changelog](https://img.shields.io/github/v/tag/jenkinsci/naginator-plugin?label=changelog)](https://github.com/jenkinsci/naginator-plugin/blob/master/CHANGELOG.md)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/naginator?color=blue)](https://plugins.jenkins.io/naginator)
[![GitHub contributors](https://img.shields.io/github/contributors/jenkinsci/naginator-plugin?color=blue)](https://github.com/jenkinsci/naginator-plugin/graphs/contributors)


This plugin allows you to automatically reschedule a build after a build failure.

  
This can be useful in several cases, including:

-   The build is dependent upon external resources, that were
    temporarily unavailable (DB down, network down, website down, etc).
-   Users want continuous emails sent out until the build is fixed, in
    order to prompt people into action.

## Configuration

Simply install the plugin, and then check the Post-Build action "Retry
build after failure" on your project's configuration page.

If the build fails, it will be rescheduled to run again after the time
you specified. You can choose how many times to retry running the job.
For each consecutive unsuccessful build, you can choose to extend the
waiting period.

The following options are also available:

-   Rerun build for unstable builds as well as failures
-   Only rebuild the job if the build's log output contains a given
    regular expression
-   Rerun build only for the failed parts of a matrix job

The plugin also adds a rerun button for in the build section.

## Issues

To report a bug or request an enhancement to this plugin please create a
ticket in JIRA (you need to login or to sign up for an account). Also
have a look on [How to report an
issue](https://wiki.jenkins.io/display/JENKINS/How+to+report+an+issue)

-   [Bug
    report](https://issues.jenkins-ci.org/secure/CreateIssueDetails!init.jspa?pid=10172&issuetype=1&components=15560&priority=4&assignee=ikedam)
-   [Request or propose an improvement of existing
    feature](https://issues.jenkins-ci.org/secure/CreateIssueDetails!init.jspa?pid=10172&issuetype=4&components=15560&priority=4)
-   [Request or propose a new
    feature](https://issues.jenkins-ci.org/secure/CreateIssueDetails!init.jspa?pid=10172&issuetype=2&components=15560&priority=4)
-   [Open
    Issues](https://issues.jenkins-ci.org/issues/?jql=project%20%3D%20JENKINS%20AND%20status%20in%20(Open%2C%20%22In%20Progress%22%2C%20Reopened%2C%20%22In%20Review%22%2C%20Verified)%20AND%20component%20%3D%20naginator-plugin)