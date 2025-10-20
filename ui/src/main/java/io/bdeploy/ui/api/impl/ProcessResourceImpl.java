package io.bdeploy.ui.api.impl;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import io.bdeploy.bhive.BHive;
import io.bdeploy.interfaces.VerifyOperationResultDto;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.remote.versioning.VersionMismatchFilter;
import io.bdeploy.ui.api.ProcessResource;
import io.bdeploy.ui.dto.InstanceProcessStatusDto;
import io.bdeploy.ui.dto.MappedInstanceProcessStatusDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

public class ProcessResourceImpl implements ProcessResource {

    private final BHive hive;
    private final String group;
    private final String instance;

    @Inject
    private MasterProvider mp;

    @Context
    private SecurityContext context;

    public ProcessResourceImpl(BHive hive, String group, String instance) {
        this.hive = hive;
        this.group = group;
        this.instance = instance;
    }

    @Override
    public InstanceProcessStatusDto getStatus() {
        MasterNamedResource master = getMasterResource();
        InstanceStatusDto instanceStatus = master.getStatus(instance);

        InstanceProcessStatusDto result = new InstanceProcessStatusDto();
        result.processStates = instanceStatus.getAppStatus();
        result.processToNode = new TreeMap<>();

        for (var app : result.processStates.keySet()) {
            // The getStatus() endpoint is here for backwards compatibility, and we expect only old servers and CLIs to use it.
            // Seeing the messages bellow signify API misuse.
            result.processToNode.put(app, instanceStatus.getSingleNodeThat(nodeStatus -> nodeStatus.isAppDeployed(app),
                    "Cannot get status from app deployed on multiple nodes."));

            var running = instanceStatus.getSingleNodeThat(nodeStatus -> nodeStatus.isAppRunningOrScheduled(app),
                    "Cannot get status from app running or scheduled on multiple nodes.");

            if (running != null) {
                // takes precedence
                result.processToNode.put(app, running);
            }
        }

        return result;
    }

    @Override
    public MappedInstanceProcessStatusDto getMappedStatus() {
        MasterNamedResource master = getMasterResource();
        InstanceStatusDto instanceStatus = master.getStatus(instance);

        MappedInstanceProcessStatusDto result = new MappedInstanceProcessStatusDto();
        result.processStates = instanceStatus.getAppsOnServerNodesStatus();
        result.multiNodeToRuntimeNode = new TreeMap<>();
        result.processToNode = new TreeMap<>();

        result.processStates.forEach((app, nodeToStatusMap) -> {
            nodeToStatusMap.keySet().stream().findFirst().ifPresent(serverNode -> {
                // taking the first server node in the map - either it is the single
                // server node that this was configured to run on or it is
                // a multi runtime node that this belongs to
                String configuredNode = instanceStatus.identifyTopLevelNode(serverNode);
                result.processToNode.put(app, configuredNode);
                if (instanceStatus.isMultiNode(configuredNode)) {
                    // assume all nodes in the status belong to this
                    result.multiNodeToRuntimeNode.put(configuredNode, nodeToStatusMap.keySet());
                }
            });
        });
        return result;
    }

    @Override
    public ProcessDetailDto getDetails(String runtimeNode, String appId) {
        MasterNamedResource master = getMasterResource();
        try {
            return master.getProcessDetailsFromNode(instance, appId, runtimeNode);
        } catch (WebApplicationException wea) {
            if (wea.getResponse().getStatus() == VersionMismatchFilter.CODE_VERSION_MISMATCH) {
                return master.getProcessDetails(instance, appId);
            }
            throw wea;
        }
    }

    @Override
    public void startProcesses(List<String> processIds) {
        MasterNamedResource master = getMasterResource();
        master.start(instance, processIds);
    }

    @Override
    public void startProcesses(Map<String, List<String>> node2Applications) {
        MasterNamedResource master = getMasterResource();
        try {
            master.start(instance, node2Applications);
        } catch (WebApplicationException wea) {
            if (wea.getResponse().getStatus() == VersionMismatchFilter.CODE_VERSION_MISMATCH) {
                master.start(instance, node2Applications.values().stream().flatMap(List::stream).collect(Collectors.toList()));
            }
            throw wea;
        }
    }

    @Override
    public void stopProcesses(List<String> processIds) {
        MasterNamedResource master = getMasterResource();
        master.stop(instance, processIds);
    }

    @Override
    public void stopProcesses(Map<String, List<String>> node2Applications) {
        MasterNamedResource master = getMasterResource();
        try {
            master.stop(instance, node2Applications);
        } catch (WebApplicationException wea) {
            if (wea.getResponse().getStatus() == VersionMismatchFilter.CODE_VERSION_MISMATCH) {
                master.stop(instance, node2Applications.values().stream().flatMap(List::stream).collect(Collectors.toList()));
            }
            throw wea;
        }
    }

    @Override
    public void restartProcesses(Map<String, List<String>> node2Applications) {
        MasterNamedResource master = getMasterResource();
        try {
            master.stop(instance, node2Applications);
            master.start(instance, node2Applications);
        } catch (WebApplicationException wea) {
            if (wea.getResponse().getStatus() == VersionMismatchFilter.CODE_VERSION_MISMATCH) {
                master.stop(instance, node2Applications.values().stream().flatMap(List::stream).collect(Collectors.toList()));
                master.start(instance, node2Applications.values().stream().flatMap(List::stream).collect(Collectors.toList()));
            }
            throw wea;
        }
    }

    @Override
    public void restartProcesses(List<String> processIds) {
        MasterNamedResource master = getMasterResource();
        master.stop(instance, processIds);
        master.start(instance, processIds);
    }

    @Override
    public void startAll() {
        MasterNamedResource master = getMasterResource();
        master.start(instance);
    }

    @Override
    public void stopAll() {
        MasterNamedResource master = getMasterResource();
        master.stop(instance);
    }

    @Override
    public void restartAll() {
        stopAll();
        startAll();
    }

    @Override
    public List<RemoteDirectory> getDataDirSnapshot() {
        MasterNamedResource master = getMasterResource();
        return master.getDataDirectorySnapshots(instance);
    }

    @Override
    public List<RemoteDirectory> getLogDataDirSnapshot() {
        MasterNamedResource master = getMasterResource();
        return master.getLogDataDirectorySnapshots(instance);
    }

    @Override
    public void writeToStdin(String processId, String node, String data) {
        MasterNamedResource master = getMasterResource();
        master.writeToStdin(instance, processId, node, data);
    }

    private MasterNamedResource getMasterResource() {
        InstanceManifest manifest = InstanceManifest.load(hive, instance, null);
        MasterRootResource root = ResourceProvider.getVersionedResource(mp.getControllingMaster(hive, manifest.getKey()),
                MasterRootResource.class, context);
        return root.getNamedMaster(group);
    }

    @Override
    public VerifyOperationResultDto verify(String appId, String runtimeNode) {
        MasterNamedResource master = getMasterResource();
        return master.verify(instance, appId, runtimeNode);
    }

    @Override
    public void reinstall(String appId, String runtimeNode) {
        MasterNamedResource master = getMasterResource();
        master.reinstall(instance, appId, runtimeNode);
    }

}
