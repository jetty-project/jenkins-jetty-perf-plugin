package org.mortbay.jetty.perf.jenkins;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.jar.JarEntry;

public class JdkPathFinder extends Builder implements SimpleBuildStep
{

    private static final Logger LOGGER = LoggerFactory.getLogger(JdkPathFinder.class);

    private String[] nodes;
    private String[] jdkNames;
    private String propertiesFileSuffix;

    @DataBoundConstructor
    public JdkPathFinder(String[] nodes, String[] jdkNames)
    {
        this.nodes = nodes;
        this.jdkNames = jdkNames;
    }

    public String[] getNodes()
    {
        return nodes;
    }

    public String[] getJdkNames()
    {
        return jdkNames;
    }

    @DataBoundSetter
    public void setNodes( String[] nodes )
    {
        this.nodes = nodes;
    }

    @DataBoundSetter
    public void setJdkNames( String[] jdkNames )
    {
        this.jdkNames = jdkNames;
    }

    public String getPropertiesFileSuffix()
    {
        return propertiesFileSuffix;
    }

    @DataBoundSetter
    public void setPropertiesFileSuffix( String propertiesFileSuffix )
    {
        this.propertiesFileSuffix = propertiesFileSuffix;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        LOGGER.info( "searching on nodes: {} for jdks: {}", Arrays.asList(nodes), Arrays.asList(jdkNames));
        for (String nodeName : nodes)
        {
            FilePath jdkPropertiesFile =
                workspace.child(nodeName +
                                    (StringUtils.isEmpty(propertiesFileSuffix)?"-jdk-paths.properties":propertiesFileSuffix));
            Node node = Jenkins.get().getNode(nodeName);
            // if not search by label
            if (node == null)
            {
                // we assume only one...
                node = Jenkins.get().getNodes().stream()
                    .filter(node1 -> node1.getLabelString().contains(nodeName))
                    .findFirst()
                    .orElse(null);
            }
            Properties properties = new Properties();
            for (String jdkName : jdkNames)
            {
                JDK jdk = new JDK(jdkName, null );
                jdk = jdk.forNode(node, listener);
                String home = jdk.getHome();
                if(StringUtils.isNotEmpty(home))
                {
                    properties.put( jdkName, home );
                }
            }
            if (jdkPropertiesFile.exists())
            {
                jdkPropertiesFile.delete();
            }
            properties.store(jdkPropertiesFile.write(), "paths of node jdks");
        }
    }

    @Symbol("jdkpathfinder")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {


        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return JdkPathFinder.class.getName();
        }

    }

}
