package io.bdeploy.minion.remote.jersey;

import java.io.File;
import java.nio.file.Path;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;

import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.directory.InstanceDirectoryEntry;
import io.bdeploy.interfaces.remote.SlaveProcessResource;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.pcu.InstanceProcessController;
import io.bdeploy.pcu.MinionProcessController;
import io.bdeploy.pcu.ProcessController;

public class SlaveProcessResourceImpl implements SlaveProcessResource {

    @Inject
    private MinionRoot root;

    @Override
    public void start(String instanceId) {
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController instanceController = processController.get(instanceId);
        if (instanceController == null) {
            throw new WebApplicationException("Instance with ID '" + instanceId + "' is unknown");
        }
        instanceController.startAll();
    }

    @Override
    public void start(String instanceId, String applicationId) {
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController instanceController = processController.get(instanceId);
        if (instanceController == null) {
            throw new WebApplicationException("Instance with ID '" + instanceId + "' is unknown");
        }
        instanceController.start(applicationId);
    }

    @Override
    public void stop(String instanceId) {
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController instanceController = processController.get(instanceId);
        if (instanceController == null) {
            throw new WebApplicationException("Instance with ID '" + instanceId + "' is unknown");
        }
        instanceController.stopAll();
    }

    @Override
    public void stop(String instanceId, String applicationId) {
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController instanceController = processController.get(instanceId);
        if (instanceController == null) {
            throw new WebApplicationException("Instance with ID '" + instanceId + "' is unknown");
        }
        instanceController.stop(applicationId);
    }

    @Override
    public InstanceNodeStatusDto getStatus(String instanceId) {
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController instanceController = processController.get(instanceId);
        if (instanceController == null) {
            return new InstanceNodeStatusDto();
        }
        return instanceController.getStatus();
    }

    @Override
    public InstanceDirectoryEntry getOutputEntry(String instanceId, String tag, String applicationId) {
        DeploymentPathProvider dpp = new DeploymentPathProvider(root.getDeploymentDir().resolve(instanceId), tag);
        Path runtime = dpp.get(SpecialDirectory.RUNTIME);
        Path out = runtime.resolve(applicationId).resolve(ProcessController.OUT_TXT);
        File file = out.toFile();

        if (file.exists()) {
            InstanceDirectoryEntry ide = new InstanceDirectoryEntry();
            ide.path = PathHelper.separatorsToUnix(runtime.relativize(out));
            ide.root = SpecialDirectory.RUNTIME;
            ide.lastModified = file.lastModified();
            ide.size = file.length();
            ide.tag = tag;
            ide.uuid = instanceId;

            return ide;
        }

        return null;
    }

    @Override
    public void writeToStdin(String instanceId, String applicationId, String data) {
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController instanceController = processController.get(instanceId);
        if (instanceController == null) {
            throw new WebApplicationException("Instance with ID '" + instanceId + "' is unknown");
        }
        instanceController.writeToStdin(applicationId, data);
    }
}
