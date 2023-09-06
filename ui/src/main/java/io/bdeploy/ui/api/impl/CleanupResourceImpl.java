package io.bdeploy.ui.api.impl;

import java.util.List;

import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.interfaces.cleanup.CleanupGroup;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.plugin.VersionSorterService;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.ui.api.CleanupResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.cleanup.CleanupHelper;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

public class CleanupResourceImpl implements CleanupResource {

    @Inject
    private Minion minion;

    @Inject
    private MasterProvider provider;

    @Inject
    private BHiveRegistry registry;

    @Inject
    private VersionSorterService vss;

    @Context
    private SecurityContext context;

    @Inject
    private ActionFactory af;

    @Override
    public List<CleanupGroup> calculate() {
        try (ActionHandle h = af.run(Actions.CLEANUP_CALCULATE)) {
            checkMaster();
            CleanupHelper ch = new CleanupHelper(context, minion, registry, provider, vss);
            return ch.calculate();
        }
    }

    @Override
    public void perform(List<CleanupGroup> groups) {
        try (ActionHandle h = af.run(Actions.CLEANUP_PERFORM)) {
            checkMaster();
            CleanupHelper ch = new CleanupHelper(context, minion, registry, provider, vss);
            ch.execute(groups);
        }
    }

    private void checkMaster() {
        if (!minion.isMaster()) {
            throw new WebApplicationException("Cleanup is only supported on the master", Status.BAD_REQUEST);
        }
    }

}
