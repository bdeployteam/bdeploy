package io.bdeploy.minion.remote.jersey;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.Version;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.Threads;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.remote.CommonUpdateResource;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.minion.MinionRoot;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;

public class CentralUpdateResourceImpl implements CommonUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(CentralUpdateResourceImpl.class);

    @Inject
    private BHiveRegistry registry;

    @Inject
    private MinionRoot root;

    @Context
    private ResourceContext rc;

    @Inject
    private ActionFactory actions;

    @Override
    public Version getUpdateApiVersion() {
        return UpdateHelper.currentApiVersion();
    }

    @Override
    public void updateV1(Key version, boolean clean) {
        try (ActionHandle h = actions.run(Actions.UPDATE, null, null, version.getTag())) {
            BHive bhive = registry.get(JerseyRemoteBHive.DEFAULT_NAME);

            Set<Key> keys = bhive.execute(new ManifestListOperation().setManifestName(version.toString()));
            if (!keys.contains(version)) {
                throw new WebApplicationException("Key not found: + " + version, Status.NOT_FOUND);
            }

            // find target OS for update package
            OperatingSystem updateOs = getTargetOsFromUpdate(version);

            if (OsHelper.getRunningOs() == updateOs) {
                try {
                    MinionUpdateResourceImpl updateResource = rc.initResource(new MinionUpdateResourceImpl());
                    updateResource.prepare(version, clean);
                    updateResource.update(version);
                } catch (Exception e) {
                    log.error("Problems updating central", e);
                    WebApplicationException ex = new WebApplicationException("Problem updating central",
                            Status.INTERNAL_SERVER_ERROR);
                    ex.addSuppressed(e);
                }
            } else {
                log.info("Skipping update -- not central's OS (update: {}, central: {}", updateOs, OsHelper.getRunningOs());
            }
        }
    }

    private static OperatingSystem getTargetOsFromUpdate(Key version) {
        ScopedManifestKey scoped = ScopedManifestKey.parse(version);
        if (scoped == null) {
            throw new IllegalStateException("Cannot determine OS from key " + version);
        }

        return scoped.getOperatingSystem();
    }

    @Override
    public void restartServer() {
        // never-ending restart-server action which will notify the web-ui of pending restart.
        actions.run(Actions.RESTART_SERVER);
        root.getServerProcessManager().performRestart(1_000);
    }

    @Override
    public void createStackDump() {
        Threads.dump(root.getLogDir(), "Running-Threads.dump");
    }

}
