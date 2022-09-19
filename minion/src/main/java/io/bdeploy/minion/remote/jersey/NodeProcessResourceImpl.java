package io.bdeploy.minion.remote.jersey;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.history.runtime.MinionRuntimeHistory;
import io.bdeploy.interfaces.manifest.history.runtime.MinionRuntimeHistoryDto;
import io.bdeploy.interfaces.remote.NodeProcessResource;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.pcu.InstanceProcessController;
import io.bdeploy.pcu.MinionProcessController;
import io.bdeploy.pcu.ProcessController;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

public class NodeProcessResourceImpl implements NodeProcessResource {

    @Inject
    private MinionRoot root;

    @Context
    private SecurityContext context;

    @Override
    public void start(String instanceId) {
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController instanceController = processController.get(instanceId);
        if (instanceController == null) {
            throw new WebApplicationException("Instance with ID '" + instanceId + "' is unknown");
        }
        instanceController.startAll(context.getUserPrincipal().getName());
    }

    @Override
    public void start(String instanceId, String applicationId) {
        start(instanceId, List.of(applicationId));
    }

    @Override
    public void start(String instanceId, List<String> applicationIds) {
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController instanceController = processController.get(instanceId);
        if (instanceController == null) {
            throw new WebApplicationException("Instance with ID '" + instanceId + "' is unknown");
        }

        if (applicationIds.isEmpty()) {
            return; // nothing to do.
        }

        instanceController.start(applicationIds, context.getUserPrincipal().getName());
    }

    @Override
    public void stop(String instanceId) {
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController instanceController = processController.get(instanceId);
        if (instanceController == null) {
            throw new WebApplicationException("Instance with ID '" + instanceId + "' is unknown");
        }
        instanceController.stopAll(context.getUserPrincipal().getName());
    }

    @Override
    public void stop(String instanceId, String applicationId) {
        stop(instanceId, List.of(applicationId));
    }

    @Override
    public void stop(String instanceId, List<String> applicationIds) {
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController instanceController = processController.get(instanceId);
        if (instanceController == null) {
            throw new WebApplicationException("Instance with ID '" + instanceId + "' is unknown");
        }

        if (applicationIds.isEmpty()) {
            return; // nothing to do.
        }

        instanceController.stop(applicationIds, context.getUserPrincipal().getName());
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
    public ProcessDetailDto getProcessDetails(String instanceId, String appId) {
        MinionProcessController processController = root.getProcessController();
        return processController.getDetails(instanceId, appId);
    }

    @Override
    public RemoteDirectoryEntry getOutputEntry(String instanceId, String tag, String applicationId) {
        DeploymentPathProvider dpp = new DeploymentPathProvider(root.getDeploymentDir().resolve(instanceId), tag);
        Path runtime = dpp.get(SpecialDirectory.RUNTIME);
        Path out = runtime.resolve(applicationId).resolve(ProcessController.OUT_TXT);
        File file = out.toFile();

        if (file.exists()) {
            RemoteDirectoryEntry ide = new RemoteDirectoryEntry();
            ide.path = PathHelper.separatorsToUnix(runtime.relativize(out));
            ide.root = SpecialDirectory.RUNTIME;
            ide.lastModified = file.lastModified();
            ide.size = file.length();
            ide.tag = tag;
            ide.id = instanceId;

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

    @Override
    public MinionRuntimeHistoryDto getRuntimeHistory(String instanceId) {
        MinionRuntimeHistoryDto dto = new MinionRuntimeHistoryDto();

        BHive hive = root.getHive();
        for (Key key : hive.execute(new ManifestListOperation().setManifestName(instanceId + "/"))) {
            MinionRuntimeHistory history = InstanceNodeManifest.of(hive, key).getRuntimeHistory(hive).getFullHistory();
            if (history.isEmpty()) {
                continue;
            }
            dto.add(key.getTag(), history);
        }
        return dto;
    }
}
