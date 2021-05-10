package org.mortbay.jetty.perf.jenkins;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.toolchain.model.PersistedToolchains;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.apache.maven.toolchain.model.io.xpp3.MavenToolchainsXpp3Writer;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * generate a toolchain format file for each nodes/jdkname.
 * the generated file will be name `nodename`-toolchains.xml
 */
public class JdkPathFinderStep extends Step
{

    private static final Logger LOGGER = LoggerFactory.getLogger(JdkPathFinderStep.class);

    private String[] nodes;
    private String[] jdkNames;
    private String propertiesFileSuffix;

    @DataBoundConstructor
    public JdkPathFinderStep(String[] nodes, String[] jdkNames)
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
    public void setNodes(String[] nodes)
    {
        this.nodes = nodes;
    }

    @DataBoundSetter
    public void setJdkNames(String[] jdkNames)
    {
        this.jdkNames = jdkNames;
    }

    public String getPropertiesFileSuffix()
    {
        return propertiesFileSuffix;
    }

    @DataBoundSetter
    public void setPropertiesFileSuffix(String propertiesFileSuffix)
    {
        this.propertiesFileSuffix = propertiesFileSuffix;
    }

    @Override
    public StepExecution start(StepContext stepContext) throws Exception
    {
        return new Execution(this, stepContext);
    }

    public static final class Execution extends SynchronousNonBlockingStepExecution<Void>
    {

        private final transient JdkPathFinderStep step;

        Execution(JdkPathFinderStep step, StepContext context)
        {
            super(context);
            this.step = step;
        }

        protected Void run() throws Exception
        {

            LOGGER.info("searching on nodes: {} for jdks: {}", Arrays.asList(step.nodes), Arrays.asList(step.jdkNames));
            String type = "jdk";
            FilePath workspace = getContext().get(FilePath.class);
            TaskListener listener = getContext().get(TaskListener.class);

            for (String nodeName : step.nodes)
            {
                PersistedToolchains persistedToolchains = new PersistedToolchains();

                FilePath jdkPropertiesFile = workspace.child(nodeName + (StringUtils.isEmpty(step.propertiesFileSuffix)
                    ? "-toolchains.xml"
                    : step.propertiesFileSuffix));
                Node node = "master".equals(nodeName) ? Jenkins.get() : Jenkins.get().getNode(nodeName);
                // if not search by label
                if (node == null)
                {
                    // we assume only one...
                    node = Jenkins.get().getNodes().stream().filter(
                        node1 -> node1.getLabelString().contains(nodeName)).findFirst().orElse(null);
                }

                for (String jdkName : step.jdkNames)
                {
                    for (ToolDescriptor<?> desc : ToolInstallation.all())
                    {
                        if (!desc.getId().equals(type) && !SymbolLookup.getSymbolValue(desc).contains(type))
                        {
                            continue;
                        }
                        for (ToolInstallation tool : desc.getInstallations())
                        {
                            if (tool.getName().equals(jdkName))
                            {
                                // install if not jdk for the node as JDK is NodeSpecific
                                if (tool instanceof NodeSpecific)
                                {
                                    tool = (ToolInstallation)((NodeSpecific<?>)tool)
                                        .forNode(node, listener);
                                }
                                if (tool instanceof EnvironmentSpecific)
                                {
                                    tool = (ToolInstallation)((EnvironmentSpecific<?>)tool)
                                        .forEnvironment(getContext().get(EnvVars.class));
                                }

                                String home = tool.getHome();
                                if (StringUtils.isNotEmpty(home))
                                {
                                    ToolchainModel toolchainModel = new ToolchainModel();
                                    toolchainModel.setType("jdk");
                                    Xpp3Dom provides = new Xpp3Dom("provides");
                                    Xpp3Dom version = new Xpp3Dom("version");
                                    version.setValue(jdkName);
                                    provides.addChild(version);
                                    toolchainModel.setProvides(provides);
                                    Xpp3Dom dom = new Xpp3Dom("configuration");
                                    Xpp3Dom jdkHome = new Xpp3Dom("jdkHome");
                                    jdkHome.setValue(home);
                                    dom.addChild(jdkHome);
                                    toolchainModel.setConfiguration(dom);
                                    persistedToolchains.addToolchain(toolchainModel);
                                }
                                else
                                {
                                    listener.error("node " + nodeName + " cannot find jdkHome for jdk: " + jdkName);
                                }
                            }
                        }
                    }
                }

                if (jdkPropertiesFile.exists())
                {
                    jdkPropertiesFile.delete();
                }
                try (StringWriter stringWriter = new StringWriter())
                {
                    MavenToolchainsXpp3Writer writer = new MavenToolchainsXpp3Writer();
                    writer.write(stringWriter, persistedToolchains);
                    jdkPropertiesFile.write().write(stringWriter.toString().getBytes(StandardCharsets.UTF_8));
                }

            }
            return null;
        }

        private static final long serialVersionUID = 1L;

    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor
    {

        @Override
        public String getDisplayName()
        {
            return JdkPathFinderStep.class.getName();
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext()
        {
            return Collections.unmodifiableSet(
                new HashSet<>(Arrays.asList(TaskListener.class, EnvVars.class, Node.class, FilePath.class)));
        }

        @Override
        public String getFunctionName()
        {
            return "jdkpathfinder";
        }
    }

}
