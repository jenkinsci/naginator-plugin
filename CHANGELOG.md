# Changelog

### 1.18.2

Release date: (TBD), 2022

* *SECURITY FIX* (SECURITY-2946)
    * https://www.jenkins.io/security/advisory/(TBD)/

### 1.18.1

Release date: Aug 9, 2020

* Move the documentation to Github
(https://github.com/jenkinsci/naginator-plugin/pull/48[#48])

### 1.18

Release date: Feb 23, 2019

-   Now targets Jenkins 1.565+ (was 1.554+)
-   FIXED: wrong delay time calcuration in "Progressive" strategy when
    there're multiple series of retries in parallel: [
    JENKINS-43803](https://issues.jenkins-ci.org/browse/JENKINS-43803) -
    Getting issue details... STATUS
-   FIXED: links to the source builds in cause texts are broken when the
    build number gets 1000+: [
    JENKINS-50751](https://issues.jenkins-ci.org/browse/JENKINS-50751) -
    Getting issue details... STATUS

### 1.17.2

Release date: Aug 14, 2016

-   Don't display "Retry" link for pipeline builds
    ([JENKINS-36438](https://issues.jenkins-ci.org/browse/JENKINS-36438))
    -   It caused NPE. Naginator doesn't works for pipeline builds for
        now.

### 1.17.1

Release date: Jun 05, 2016

-   Fix NPE when a single Maven module is triggered
    ([JENKINS-34900](https://issues.jenkins-ci.org/browse/JENKINS-34900))

### 1.17

Release date: Mar 12, 2016

-   Makes the build number of the naginator cause a clickable link
    ([\#25](https://github.com/jenkinsci/naginator-plugin/pull/25))
-   Provides variables NAGINATOR\_COUNT, NAGINATOR\_MAXCOUNT,
    NAGINATOR\_BUILD\_NUMBER
    ([JENKINS-21241](https://issues.jenkins-ci.org/browse/JENKINS-21241)).
-   Displays the checkbox for matrix projects even when the publisher is
    newly added
    ([JENKINS-32822](https://issues.jenkins-ci.org/browse/JENKINS-32822)).
-   Added "When no combinations to rerun"
    ([JENKINS-32823](https://issues.jenkins-ci.org/browse/JENKINS-32823)).
-   Introduced a new option "How to apply the regular expression to
    matrix" which replaces "Test regular expression for the matrix
    parent"
    ([JENKINS-32821](https://issues.jenkins-ci.org/browse/JENKINS-32821))

### 1.16.1

Release date: Dec 06, 2015

-   Fixed: Retry button invisible even with "Build" permission for a
    project
    ([JENKINS-31318](https://issues.jenkins-ci.org/browse/JENKINS-31318))
-   Fixed: Bad regular expressions cause unabortable builds
    ([JENKINS-24903](https://issues.jenkins-ci.org/browse/JENKINS-24903))
    -   Too long running regular expressions will be aborted. You can
        change the timeout for regular expressions in Configure System
        page.

### 1.16

Release date: Oct 31, 2015

-   Allow regexps applied for matrix children logs
    ([JENKINS-26637](https://issues.jenkins-ci.org/browse/JENKINS-26637))
-   Count the number a build rescheduled precisely
    ([JENKINS-17626](https://issues.jenkins-ci.org/browse/JENKINS-17626))
-   Control rescheduling with action
    ([JENKINS-29715](https://issues.jenkins-ci.org/browse/JENKINS-29715),
    [JENKINS-23984](https://issues.jenkins-ci.org/browse/JENKINS-23984))
    -   Other plugins can have naginator plugin retrigger a build by
        adding
        [NaginatorScheduleAction](https://github.com/jenkinsci/naginator-plugin/blob/master/src/main/java/com/chikli/hudson/plugin/naginator/NaginatorScheduleAction.java)
        to the build.

### 1.15

Release date: Apr 9, 2014

-   Make JobProperty optional for jobs
-   Decrease require core for ancient jenkins users

### 1.14

Release date: Dec 19, 2014

-   Retain original build causes on manual retry
    ([JENKINS-20065](https://issues.jenkins-ci.org/browse/JENKINS-20065))
-   Add details and optimize logging
    ([JENKINS-26118](https://issues.jenkins-ci.org/browse/JENKINS-26118))
-   Ensure BufferedReader in parseLog is closed
    ([JENKINS-25800](https://issues.jenkins-ci.org/browse/JENKINS-25800))

### 1.13

Release date: Nov 12, 2014

-   Fix progressive delay time calculation (behavior slightly changed)
-   Fix rerun behavior for unstable builds in matrix
-   Better log the trigger cause
-   Fix badge icon for the case in which Jenkins is not in the root
    folder
-   Don't show rerun link if user doesn't have permissions

### 1.12

Release date: Aug 25, 2014

-   Added the option to rerun only the failed parts of a matrix
-   Retry will occur only when all parts of a matrix finish
-   Rebuild link verify authentication
-   Don't rerun job on manual cancel
-   Fix NPE when running Maven build

### 1.11

Release date: April 8, 2014

-   Naginator now retain original build causes on retry

### 1.9

Release date: Nov 8, 2013

-   Re-schedule limit doesn't consider previous builds that aren't
    related to Naginator
-   Added a badge icon to re-scheduled builds
-   Bug fixes

### 1.8

Release date: June 12, 2012

-   New extension point to configure schedule delay
-   Fixed delay implementation
-   Parameters for build are reused on schedule
-   Limit for number of build attempts after failure

### 1.7

Release date: May 31, 2012

-   Fix NPE for non-nagged jobs
    [JENKINS-13791](https://issues.jenkins-ci.org/browse/JENKINS-13791)

### 1.6.1

Release date: May 3, 2012

-   Fix compatibility with build-timeout plugin
    ([JENKINS-11594](https://issues.jenkins-ci.org/browse/JENKINS-11594))
-   Use a RunListener

### 1.6

-   Not released (release:prepare failed on ndeloof computer :-/)

### 1.5

Release date: Dec 7, 2009

-   Added support for not rebuilding if the build is unstable.
-   Added support for only rebuilding if a regular expression is found
    in the build log.

### 1.4

Release date: Jan 26, 2009

-   The plugin progressively introduces a delay until the next build. It
    starts with 5 minutes and goes up to one hour.

### 1.3

Release date: April 9, 2008

-   After way too long, the release is actually out there. 1.1 and 1.2
    are missing due to my inability to use the maven release process
    correctly.

### 1.0 

Release date: Sept 17, 2007

-   Initial Release - release didn't actually make it to the
    repository...
