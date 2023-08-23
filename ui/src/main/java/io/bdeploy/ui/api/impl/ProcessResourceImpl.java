package io.bdeploy.ui.api.impl;

import java.util.List;
import java.util.TreeMap;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.NoThrowAutoCloseable;
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
    public InstanceProcessStatusDto getStatus() {
        MasterNamedResource master = getMasterResource();
        InstanceStatusDto instanceStatus = master.getStatus(instanceId);

        InstanceProcessStatusDto result = new InstanceProcessStatusDto();
        result.processStates = instanceStatus.getAppStatus();
        result.processToNode = new TreeMap<>();

        for (var app : result.processStates.keySet()) {
            result.processToNode.put(app, instanceStatus.getNodeWhereAppIsDeployed(app));

            var running = instanceStatus.getNodeWhereAppIsRunningOrScheduled(app);
            if (running != null) {
                // takes precedence;
                result.processToNode.put(app, running);
            }
        }

        return result;
    }

    @Override
    public ProcessDetailDto getDetails(String nodeName, String appId) {
        MasterNamedResource master = getMasterResource();
        try {
            return master.getProcessDetailsFromNode(instanceId, appId, nodeName);
        } catch (WebApplicationException wea) {
            if (wea.getResponse().getStatus() == VersionMismatchFilter.CODE_VERSION_MISMATCH) {
                return master.getProcessDetails(instanceId, appId);
            }
            throw wea;
        }
    }

    @Override
    public void startProcesses(List<String> processIds) {
        MasterNamedResource master = getMasterResource();
        InstanceManifest manifest = InstanceManifest.load(hive, instanceId, null);
        try (Activity activity = reporter.start("Launching");
                NoThrowAutoCloseable proxy = reporter.proxyActivities(mp.getControllingMaster(hive, manifest.getManifest()))) {
            try {
                master.start(instanceId, processIds);
            } catch (WebApplicationException wea) {
                // Compatibility with pre-4.2.0
                if (wea.getResponse().getStatus() == VersionMismatchFilter.CODE_VERSION_MISMATCH) {
                    for (var p : processIds) {
                        master.start(instanceId, p);
                    }
                } else {
                    throw wea;
                }
            }
        }
    }

    @Override
    public void stopProcesses(List<String> processIds) {
        MasterNamedResource master = getMasterResource();
        InstanceManifest manifest = InstanceManifest.load(hive, instanceId, null);
        try (Activity activity = reporter.start("Stopping");
                NoThrowAutoCloseable proxy = reporter.proxyActivities(mp.getControllingMaster(hive, manifest.getManifest()))) {
            try {
                master.stop(instanceId, processIds);
            } catch (WebApplicationException wea) {
                // Compatibility with pre-4.2.0
                if (wea.getResponse().getStatus() == VersionMismatchFilter.CODE_VERSION_MISMATCH) {
                    for (var p : processIds) {
                        master.stop(instanceId, p);
                    }
                } else {
                    throw wea;
                }
            }
        }
    }

    @Override
    public void restartProcesses(List<String> processIds) {
        MasterNamedResource master = getMasterResource();
        InstanceManifest manifest = InstanceManifest.load(hive, instanceId, null);
        try (Activity activity = reporter.start("Restarting");
                NoThrowAutoCloseable proxy = reporter.proxyActivities(mp.getControllingMaster(hive, manifest.getManifest()))) {
            try {
                master.stop(instanceId, processIds);
                master.start(instanceId, processIds);
            } catch (WebApplicationException wea) {
                // Compatibility with pre-4.2.0
                if (wea.getResponse().getStatus() == VersionMismatchFilter.CODE_VERSION_MISMATCH) {
                    for (var p : processIds) {
                        master.stop(instanceId, p);
                        master.start(instanceId, p);
                    }
                } else {
                    throw wea;
                }
            }
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
    public void restartAll() {
        InstanceManifest manifest = InstanceManifest.load(hive, instanceId, null);
        try (Activity activity = reporter.start("Restarting All");
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
