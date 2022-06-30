package io.bdeploy.ui.api.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.SystemManifest;
import io.bdeploy.interfaces.manifest.managed.ControllingMaster;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.manifest.managed.ManagedMasters;
import io.bdeploy.interfaces.manifest.managed.ManagedMastersConfiguration;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.MasterSystemResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.SystemResource;
import io.bdeploy.ui.dto.ObjectChangeType;
import io.bdeploy.ui.dto.SystemConfigurationDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriBuilder;

public class SystemResourceImpl implements SystemResource {

    private final String group;
    private final BHive hive;

    @Context
    private Minion minion;

    @Context
    private SecurityContext context;

    @Context
    private ResourceContext rc;

    @Inject
    private MasterProvider mp;

    @Inject
    private ChangeEventManager changes;

    public SystemResourceImpl(String group, BHive hive) {
        this.group = group;
        this.hive = hive;
    }

    @Override
    public List<SystemConfigurationDto> list() {
        List<SystemConfigurationDto> result = new ArrayList<>();
        SortedSet<Key> systems = SystemManifest.scan(hive);
        for (Manifest.Key k : systems) {
            SystemConfigurationDto dto = new SystemConfigurationDto();

            if (minion.getMode() == MinionMode.CENTRAL) {
                dto.minion = new ControllingMaster(hive, k).read().getName();
            }

            dto.key = k;
            dto.config = SystemManifest.of(hive, k).getConfiguration();
            result.add(dto);
        }
        return result;
    }

    @Override
    public void update(SystemConfigurationDto dto) {
        SystemManifest sm = SystemManifest.load(hive, dto.config.uuid);
        String target = dto.minion;
        RemoteService remote;
        if (sm == null) {
            // new system.
            if (minion.getMode() == MinionMode.CENTRAL) {
                ManagedMastersConfiguration masters = new ManagedMasters(hive).read();
                ManagedMasterDto server = masters.getManagedMaster(target);
                if (server == null) {
                    throw new WebApplicationException("Managed server not found: " + target, Status.NOT_FOUND);
                }

                remote = new RemoteService(UriBuilder.fromUri(server.uri).build(), server.auth);
            } else {
                remote = minion.getSelf();
            }
        } else {
            // existing system.
            remote = mp.getControllingMaster(hive, sm.getKey());
        }

        MasterSystemResource msr = ResourceProvider.getVersionedResource(remote, MasterRootResource.class, context)
                .getNamedMaster(group).getSystemResource();

        msr.update(dto.config);

        syncServer(target);
    }

    @Override
    public void delete(String systemId) {
        Key key = SystemManifest.load(hive, systemId).getKey();
        RemoteService remote = mp.getControllingMaster(hive, key);
        MasterSystemResource msr = ResourceProvider.getVersionedResource(remote, MasterRootResource.class, context)
                .getNamedMaster(group).getSystemResource();

        msr.delete(systemId);
        syncSystem(key);

        if (minion.getMode() != MinionMode.CENTRAL) {
            changes.remove(ObjectChangeType.SYSTEM, key);
        }
    }

    private void syncSystem(Manifest.Key systemKey) {
        if (minion.getMode() != MinionMode.CENTRAL) {
            return;
        }

        ManagedServersResource rs = rc.initResource(new ManagedServersResourceImpl());
        String master = new ControllingMaster(hive, systemKey).read().getName();
        rs.synchronize(group, master);
    }

    private void syncServer(String name) {
        if (minion.getMode() != MinionMode.CENTRAL) {
            return;
        }

        ManagedServersResource rs = rc.initResource(new ManagedServersResourceImpl());
        rs.synchronize(group, name);
    }

}
