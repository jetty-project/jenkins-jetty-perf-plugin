package org.mortbay.jetty.perf.jenkins;

import hudson.ExtensionList;
import hudson.model.JDK;
import hudson.model.Label;
import hudson.slaves.DumbSlave;
import hudson.tools.ToolLocationNodeProperty;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.SystemUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;

public class JdkPathFinderStepTest
{
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testScriptedPipeline() throws Exception
    {
        String agentLabel = "my-agent";
        DumbSlave dumbSlave = jenkins.createOnlineSlave(Label.get(agentLabel));
        jenkins.jenkins.setJDKs( Arrays.asList(new JDK("jdk11", "/home/foo/jdk11"), new JDK("jdk16", "/home/foo/jdk16")));
        dumbSlave.getNodeProperties()
            .add(new ToolLocationNodeProperty(
                new ToolLocationNodeProperty.ToolLocation(ExtensionList.lookupSingleton(JDK.DescriptorImpl.class), "jdk11", "/home/foo/jdk11"),
                new ToolLocationNodeProperty.ToolLocation(ExtensionList.lookupSingleton(JDK.DescriptorImpl.class), "jdk16", "/home/foo/jdk16")
                ));

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "test-scripted-pipeline");
        String pipelineScript
                = "node {\n" +
                        "  jdkpathfinder nodes:['my-agent'], jdkNames: ['jdk11', 'jdk16']\n" +
                        "  sh 'cat my-agent-toolchains.xml'\n" +
                        "}";
        job.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun completedBuild = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
        if (SystemUtils.IS_OS_MAC_OSX )
        {
            jenkins.assertLogContains("<jdkHome>/home/foo/jdk11/Contents/Home</jdkHome>", completedBuild);
            jenkins.assertLogContains("<jdkHome>/home/foo/jdk16/Contents/Home</jdkHome>", completedBuild);
        }
        else
        {
            jenkins.assertLogContains("<jdkHome>/home/foo/jdk11</jdkHome>", completedBuild);
            jenkins.assertLogContains("<jdkHome>/home/foo/jdk16</jdkHome>", completedBuild);
        }
        jenkins.assertLogContains("<type>jdk</type>", completedBuild);
    }

}
