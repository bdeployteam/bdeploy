package io.bdeploy.ui.api.impl;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.interfaces.cleanup.CleanupGroup;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.ui.api.CleanupResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.cleanup.CleanupHelper;

public class CleanupResourceImpl implements CleanupResource {

    @Inject
    private Minion minion;

    @Inject
    private MasterProvider provider;

    @Inject
    private BHiveRegistry registry;

    @Context
    private SecurityContext context;

    @Override
    public List<CleanupGroup> calculate() {
        checkMaster();
        return CleanupHelper.cleanAllMinions(context, minion, registry, false, provider);
    }

    @Override
    public void perform(List<CleanupGroup> groups) {
        checkMaster();
        CleanupHelper.cleanAllMinions(context, groups, minion, registry, provider);
    }

    private void checkMaster() {
        if (!minion.isMaster()) {
            throw new WebApplicationException("Cleanup is only supported on the master", Status.BAD_REQUEST);
        }
    }

}
