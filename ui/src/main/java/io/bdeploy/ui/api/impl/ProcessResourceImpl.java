package io.bdeploy.ui.api.impl;

import java.util.List;
import java.util.TreeMap;

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
            result.processToNode.put(app, instanceStatus.getNodeWhereAppIsDeployed(app));

            var running = instanceStatus.getNodeWhereAppIsRunningOrScheduled(app);
            if (running != null) {
                // takes precedence
                result.processToNode.put(app, running);
            }
        }

        return result;
    }

    @Override
    public ProcessDetailDto getDetails(String nodeName, String appId) {
        MasterNamedResource master = getMasterResource();
        try {
            return master.getProcessDetailsFromNode(instance, appId, nodeName);
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
    public void stopProcesses(List<String> processIds) {
        MasterNamedResource master = getMasterResource();
        master.stop(instance, processIds);
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
    public void writeToStdin(String processId, String data) {
        MasterNamedResource master = getMasterResource();
        master.writeToStdin(instance, processId, data);
    }

    private MasterNamedResource getMasterResource() {
        InstanceManifest manifest = InstanceManifest.load(hive, instance, null);
        MasterRootResource root = ResourceProvider.getVersionedResource(mp.getControllingMaster(hive, manifest.getManifest()),
                MasterRootResource.class, context);
        return root.getNamedMaster(group);
    }

    @Override
    public VerifyOperationResultDto verify(String appId) {
        MasterNamedResource master = getMasterResource();
        return master.verify(instance, appId);
    }

    @Override
    public void reinstall(String appId) {
        MasterNamedResource master = getMasterResource();
        master.reinstall(instance, appId);
    }

}
