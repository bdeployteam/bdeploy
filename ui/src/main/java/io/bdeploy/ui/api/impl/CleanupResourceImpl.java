package io.bdeploy.ui.api.impl;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import org.jvnet.hk2.annotations.Optional;

import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
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

    @Context
    private SecurityContext context;

    @Override
    public List<CleanupGroup> calculate() {
        checkMaster();
        return CleanupHelper.cleanAllMinions(context, minion.getMinions(), registry, false);
    }

    @Override
    public void perform(List<CleanupGroup> groups) {
        checkMaster();
        CleanupHelper.cleanAllMinions(context, groups, minion.getMinions(), registry);
    }

    private void checkMaster() {
        if (isMaster == null || !isMaster) {
            throw new WebApplicationException("Cleanup is only supported on the master", Status.BAD_REQUEST);
        }
    }

}
