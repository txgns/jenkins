/*
 * The MIT License
 *
 * Copyright 2015 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.cli;

import hudson.model.Job;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;

/**
 * @author pjanouse
 */
public class DeleteJobCommandTest {

    private CLICommandInvoker command;

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {

        command = new CLICommandInvoker(j, new DeleteJobCommand());
    }

    @Test public void deleteJobShouldFailWithoutJobDeletePermission() throws IOException {

        j.createFreeStyleProject("aProject");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Job.READ, Jenkins.READ)
                .invokeWithArgs("aProject");

        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the Job/Delete permission"));
    }

    @Test public void deleteJobShouldFailWithoutJobReadPermission() throws IOException {

        j.createFreeStyleProject("aProject");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Job.DELETE, Jenkins.READ)
                .invokeWithArgs("aProject");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'aProject'"));
    }

    @Test public void deleteJobShouldSucceed() throws Exception {

        j.createFreeStyleProject("aProject");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Job.READ, Job.DELETE, Jenkins.READ)
                .invokeWithArgs("aProject");

        assertThat(result, succeededSilently());
        assertThat(j.jenkins.getItem("aProject"), nullValue());
    }

    @Test public void deleteJobShouldFailIfJobDoesNotExist() {

        final CLICommandInvoker.Result result = command
                .authorizedTo(Job.READ, Job.DELETE, Jenkins.READ)
                .invokeWithArgs("never_created");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'"));
    }

    @Test public void deleteJobManyShouldSucceed() throws Exception {

        j.createFreeStyleProject("aProject1");
        j.createFreeStyleProject("aProject2");
        j.createFreeStyleProject("aProject3");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Job.READ, Job.DELETE, Jenkins.READ)
                .invokeWithArgs("aProject1", "aProject2", "aProject3");

        assertThat(result, succeededSilently());
        assertThat(j.jenkins.getItem("aProject1"), nullValue());
        assertThat(j.jenkins.getItem("aProject2"), nullValue());
        assertThat(j.jenkins.getItem("aProject3"), nullValue());
    }

    @Test public void deleteJobManyShouldFailIfFirstJobDoesNotExist() throws Exception {

        j.createFreeStyleProject("aProject1");
        j.createFreeStyleProject("aProject2");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Job.READ, Job.DELETE, Jenkins.READ)
                .invokeWithArgs("never_created", "aProject1", "aProject2");

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No such job 'never_created'"));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_ERROR_TEXT));

        assertThat(j.jenkins.getItem("aProject1"), nullValue());
        assertThat(j.jenkins.getItem("aProject2"), nullValue());
        assertThat(j.jenkins.getItem("never_created"), nullValue());
    }

    @Test public void deleteJobManyShouldFailIfMiddleJobDoesNotExist() throws Exception {

        j.createFreeStyleProject("aProject1");
        j.createFreeStyleProject("aProject2");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Job.READ, Job.DELETE, Jenkins.READ)
                .invokeWithArgs("aProject1","never_created", "aProject2");

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No such job 'never_created'"));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_ERROR_TEXT));

        assertThat(j.jenkins.getItem("aProject1"), nullValue());
        assertThat(j.jenkins.getItem("aProject2"), nullValue());
        assertThat(j.jenkins.getItem("never_created"), nullValue());
    }

    @Test public void deleteJobManyShouldFailIfLastJobDoesNotExist() throws Exception {

        j.createFreeStyleProject("aProject1");
        j.createFreeStyleProject("aProject2");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Job.READ, Job.DELETE, Jenkins.READ)
                .invokeWithArgs("aProject1", "aProject2", "never_created");

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No such job 'never_created'"));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_ERROR_TEXT));

        assertThat(j.jenkins.getItem("aProject1"), nullValue());
        assertThat(j.jenkins.getItem("aProject2"), nullValue());
        assertThat(j.jenkins.getItem("never_created"), nullValue());
    }

    @Test public void deleteJobManyShouldFailIfMoreJobsDoNotExist() throws Exception {

        j.createFreeStyleProject("aProject1");
        j.createFreeStyleProject("aProject2");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Job.READ, Job.DELETE, Jenkins.READ)
                .invokeWithArgs("aProject1", "never_created1", "never_created2", "aProject2");

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created1: No such job 'never_created1'"));
        assertThat(result.stderr(), containsString("never_created2: No such job 'never_created2'"));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_ERROR_TEXT));

        assertThat(j.jenkins.getItem("aProject1"), nullValue());
        assertThat(j.jenkins.getItem("aProject2"), nullValue());
        assertThat(j.jenkins.getItem("never_created1"), nullValue());
        assertThat(j.jenkins.getItem("never_created2"), nullValue());
    }

    @Test public void deleteJobManyShouldSucceedEvenAJobIsSpecifiedTwice() throws Exception {

        j.createFreeStyleProject("aProject1");
        j.createFreeStyleProject("aProject2");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Job.READ, Job.DELETE, Jenkins.READ)
                .invokeWithArgs("aProject1", "aProject2", "aProject1");

        assertThat(result, succeededSilently());
        assertThat(j.jenkins.getItem("aProject1"), nullValue());
        assertThat(j.jenkins.getItem("aProject2"), nullValue());
    }
}
