package io.bdeploy.ui.api.impl;

import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.jvnet.hk2.annotations.Optional;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.cleanup.CleanupGroup;
import io.bdeploy.interfaces.cleanup.CleanupHelper;
import io.bdeploy.ui.api.CleanupResource;
import io.bdeploy.ui.api.Minion;

public class CleanupResourceImpl implements CleanupResource {

    @Inject
    @Optional
    @Named(Minion.MASTER)
    private Boolean isMaster;

    @Inject
    private Minion minion;

    @Inject
    private BHiveRegistry registry;

    @Override
    public List<CleanupGroup> calculate() {
        checkMaster();

        SortedSet<Key> keys = CleanupHelper.findAllUniqueKeys(registry);
        return CleanupHelper.cleanAllMinions(minion.getMinions(), keys, false);
    }

    @Override
    public void perform(List<CleanupGroup> groups) {
        checkMaster();

        SortedMap<String, RemoteService> minions = minion.getMinions();
        CleanupHelper.cleanAllMinions(groups, minions);
    }

    private void checkMaster() {
        if (isMaster == null || !isMaster) {
            throw new WebApplicationException("Cleanup is only supported on the master", Status.BAD_REQUEST);
        }
    }

}
