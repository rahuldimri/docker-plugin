package io.jenkins.docker.pipeline;

import com.google.common.collect.ImmutableSet;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import io.jenkins.docker.connector.DockerComputerConnector;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerNodeStep extends Step {

    private String dockerHost;

    private String credentialsId;

    private String image;

    private String remoteFs;

    private DockerComputerConnector connector;

    @DataBoundConstructor
    public DockerNodeStep(String image) {
        this.image = image;
    }

    public String getDockerHost() {
        return dockerHost;
    }

    @DataBoundSetter
    public void setDockerHost(String dockerHost) {
        this.dockerHost = Util.fixEmpty(dockerHost);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    public String getImage() {
        return image;
    }

    public String getRemoteFs() {
        return remoteFs;
    }

    @DataBoundSetter
    public void setRemoteFs(String remoteFs) {
        this.remoteFs = Util.fixEmpty(remoteFs);
    }

    public <T extends DockerComputerConnector & Serializable> T getConnector() {
        if (connector == null) {
            return null;
        }
        DockerNodeStepExecution.assertIsSerializableDockerComputerConnector(connector);
        return (T) connector;
    }

    @DataBoundSetter
    public void setConnector(DockerComputerConnector connector) {
        if (connector == null || connector.equals(DockerNodeStepExecution.DEFAULT_CONNECTOR)) {
            this.connector = null;
        } else {
            DockerNodeStepExecution.assertIsSerializableDockerComputerConnector(connector);
            this.connector = connector;
        }
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new DockerNodeStepExecution(context, connector, dockerHost, credentialsId, image, remoteFs);
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "dockerNode";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker Node (⚠️ Experimental)";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String uri) {
            DockerServerEndpoint.DescriptorImpl descriptor = (DockerServerEndpoint.DescriptorImpl) Jenkins.get().getDescriptorOrDie(DockerServerEndpoint.class);
            return descriptor.doFillCredentialsIdItems(item, uri);
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, FlowNode.class);
        }

        @Override public Set<? extends Class<?>> getProvidedContext() {
            // TODO can/should we provide Executor? We cannot access Executor.start(WorkUnit) from outside the package. cf. isAcceptingTasks, withContexts
            return ImmutableSet.of(Computer.class, FilePath.class, /* DefaultStepContext infers from Computer: */ Node.class, Launcher.class);
        }

        public List<Descriptor<? extends DockerComputerConnector>> getAcceptableConnectorDescriptors() {
            final List<Descriptor<? extends DockerComputerConnector>> result = new ArrayList<>();
            final DescriptorExtensionList<DockerComputerConnector, Descriptor<DockerComputerConnector>> all = DockerComputerConnector.all();
            // Note: Not all DockerComputerConnector classes are suitable
            // We have to filter the list so the user doesn't select one that isn't ok.
            for (final Descriptor<? extends DockerComputerConnector> connectorDescriptor : all) {
                final Class<? extends DockerComputerConnector> connectorClass = connectorDescriptor.getKlass().toJavaClass();
                final String reason = DockerNodeStepExecution.getReasonWhyThisIsNotASerializableDockerComputerConnector(
                        connectorClass.toGenericString(), connectorClass);
                if (reason == null) {
                    result.add(connectorDescriptor);
                }
            }
            return result;
        }
    }
}
