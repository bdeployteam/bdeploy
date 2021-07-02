package io.bdeploy.ui.api.impl;

import java.util.List;
import java.util.Map;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.ProcessResource;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

public class ProcessResourceImpl implements ProcessResource {

    private final BHive hive;
    private final String instanceGroup;
    private final String instanceId;

    @Inject
    private MasterProvider mp;

    @Context
    private SecurityContext context;

    @Inject
    private ActivityReporter reporter;

    public ProcessResourceImpl(BHive hive, String instanceGroup, String instanceId) {
        this.hive = hive;
        this.instanceGroup = instanceGroup;
        this.instanceId = instanceId;
    }

    @Override
    public Map<String, ProcessStatusDto> getStatus() {
        MasterNamedResource master = getMasterResource();
        InstanceStatusDto instanceStatus = master.getStatus(instanceId);
        return instanceStatus.getAppStatus();
    }

    @Override
    public ProcessDetailDto getDetails(String appId) {
        MasterNamedResource master = getMasterResource();
        return master.getProcessDetails(instanceId, appId);
    }

    @Override
    public void startProcess(String processId) {
        MasterNamedResource master = getMasterResource();
        InstanceManifest manifest = InstanceManifest.load(hive, instanceId, null);
        try (Activity activity = reporter.start("Launching");
                NoThrowAutoCloseable proxy = reporter.proxyActivities(mp.getControllingMaster(hive, manifest.getManifest()))) {
            master.start(instanceId, processId);
        }
    }

    @Override
    public void stopProcess(String processId) {
        MasterNamedResource master = getMasterResource();
        InstanceManifest manifest = InstanceManifest.load(hive, instanceId, null);
        try (Activity activity = reporter.start("Stopping");
                NoThrowAutoCloseable proxy = reporter.proxyActivities(mp.getControllingMaster(hive, manifest.getManifest()))) {
            master.stop(instanceId, processId);
        }
    }

    @Override
    public void restartProcess(String processId) {
        MasterNamedResource master = getMasterResource();
        InstanceManifest manifest = InstanceManifest.load(hive, instanceId, null);
        try (Activity activity = reporter.start("Restarting");
                NoThrowAutoCloseable proxy = reporter.proxyActivities(mp.getControllingMaster(hive, manifest.getManifest()))) {
            master.stop(instanceId, processId);
            master.start(instanceId, processId);
        }
    }

    @Override
    public void startAll() {
        MasterNamedResource master = getMasterResource();
        InstanceManifest manifest = InstanceManifest.load(hive, instanceId, null);
        try (Activity activity = reporter.start("Starting All");
                NoThrowAutoCloseable proxy = reporter.proxyActivities(mp.getControllingMaster(hive, manifest.getManifest()))) {
            master.start(instanceId);
        }
    }

    @Override
    public void stopAll() {
        MasterNamedResource master = getMasterResource();
        InstanceManifest manifest = InstanceManifest.load(hive, instanceId, null);
        try (Activity activity = reporter.start("Stopping All");
                NoThrowAutoCloseable proxy = reporter.proxyActivities(mp.getControllingMaster(hive, manifest.getManifest()))) {
            master.stop(instanceId);
        }
    }

    @Override
    public void restart() {
        InstanceManifest manifest = InstanceManifest.load(hive, instanceId, null);
        try (Activity activity = reporter.start("Restaring All");
                NoThrowAutoCloseable proxy = reporter.proxyActivities(mp.getControllingMaster(hive, manifest.getManifest()))) {
            stopAll();
            startAll();
        }
    }

    @Override
    public List<RemoteDirectory> getDataDirSnapshot() {
        MasterNamedResource master = getMasterResource();
        return master.getDataDirectorySnapshots(instanceId);
    }

    @Override
    public void writeToStdin(String processId, String data) {
        MasterNamedResource master = getMasterResource();
        master.writeToStdin(instanceId, processId, data);
    }

    private MasterNamedResource getMasterResource() {
        InstanceManifest manifest = InstanceManifest.load(hive, instanceId, null);
        MasterRootResource root = ResourceProvider.getVersionedResource(mp.getControllingMaster(hive, manifest.getManifest()),
                MasterRootResource.class, context);
        return root.getNamedMaster(instanceGroup);
    }

}
