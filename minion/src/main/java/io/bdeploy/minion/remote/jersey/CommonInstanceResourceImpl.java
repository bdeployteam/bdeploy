package io.bdeploy.minion.remote.jersey;

import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.EndpointsConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.remote.CommonInstanceResource;
import io.bdeploy.interfaces.remote.CommonProxyResource;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.NodeProxyResource;
import io.bdeploy.interfaces.remote.ProxiedRequestWrapper;
import io.bdeploy.interfaces.remote.ProxiedResponseWrapper;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

public class CommonInstanceResourceImpl implements CommonInstanceResource {

    private final BHive hive;

    @Inject
    private Minion minion;

    @Inject
    private NodeManager nodes;

    @Inject
    private MasterProvider mp;

    @Context
    private SecurityContext context;

    @Context
    private ResourceContext rc;

    private final String groupName;

    public CommonInstanceResourceImpl(String groupName, BHive bHive) {
        this.groupName = groupName;
        this.hive = bHive;
    }

    @Override
    public SortedMap<Manifest.Key, InstanceConfiguration> listInstanceConfigurations(boolean latest) {
        SortedSet<Key> scan = InstanceManifest.scan(hive, latest);
        SortedMap<Manifest.Key, InstanceConfiguration> result = new TreeMap<>();

        scan.stream().forEach(k -> result.put(k, InstanceManifest.of(hive, k).getConfiguration()));
        return result;
    }

    private InstanceStateRecord getInstanceState(String instance) {
        InstanceManifest im = InstanceManifest.load(hive, instance, null);
        return im.getState(hive).read();
    }

    @Override
    public SortedMap<String, EndpointsConfiguration> getAllEndpoints(String instanceId) {
        SortedMap<String, EndpointsConfiguration> result = new TreeMap<>();
        String activeTag = getInstanceState(instanceId).activeTag;
        if (activeTag == null) {
            throw new WebApplicationException("Endpoints are available only once there is an active version for " + instanceId,
                    Status.NOT_FOUND);
        }

        InstanceManifest im = InstanceManifest.load(hive, instanceId, activeTag);

        for (Manifest.Key imnk : im.getInstanceNodeManifests().values()) {
            InstanceNodeManifest inm = InstanceNodeManifest.of(hive, imnk);
            inm.getConfiguration().applications.stream().forEach(a -> result.put(a.uid, a.endpoints));
        }

        return result;
    }

    @Override
    public CommonProxyResource getProxyResource(String instanceId, String applicationId) {
        return rc.initResource(new CommonProxyResourceImpl(groupName, instanceId, applicationId, getAllEndpoints(instanceId),
                this::forward, null));
    }

    @Override
    public ProxiedResponseWrapper forward(ProxiedRequestWrapper wrapper) {
        String activeTag = getInstanceState(wrapper.instanceId).activeTag;
        if (activeTag == null) {
            throw new WebApplicationException(
                    "Endpoints are available only once there is an active version for " + wrapper.instanceId,
                    Status.PRECONDITION_FAILED);
        }

        InstanceManifest im = InstanceManifest.load(hive, wrapper.instanceId, activeTag);

        if (minion.getMode() == MinionMode.CENTRAL) {
            // forward to master
            RemoteService remote = mp.getControllingMaster(hive, im.getManifest());
            return ResourceProvider.getResource(remote, CommonRootResource.class, context).getInstanceResource(wrapper.group)
                    .forward(wrapper);
        } else {
            // forward to node
            String nodeName = null;
            for (Map.Entry<String, Manifest.Key> entry : im.getInstanceNodeManifests().entrySet()) {
                InstanceNodeManifest inm = InstanceNodeManifest.of(hive, entry.getValue());
                Optional<ApplicationConfiguration> cfg = inm.getConfiguration().applications.stream()
                        .filter(a -> a.uid.equals(wrapper.applicationId)).findFirst();

                if (cfg.isPresent()) {
                    nodeName = entry.getKey();
                }
            }

            if (nodeName == null) {
                throw new WebApplicationException(
                        "Cannot find application " + wrapper.applicationId + " in instance " + wrapper.instanceId,
                        Status.PRECONDITION_FAILED);
            }

            return nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeProxyResource.class, context).forward(wrapper);
        }
    }

}
