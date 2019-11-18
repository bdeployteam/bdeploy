package io.bdeploy.minion.remote.jersey;

import java.util.SortedSet;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.remote.CentralUpdateResource;

public class CentralUpdateResourceImpl implements CentralUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(CentralUpdateResourceImpl.class);

    @Inject
    private BHiveRegistry registry;

    @Context
    private ResourceContext rc;

    @Override
    public void update(Key version, boolean clean) {
        BHive bhive = registry.get(JerseyRemoteBHive.DEFAULT_NAME);

        SortedSet<Key> keys = bhive.execute(new ManifestListOperation().setManifestName(version.toString()));
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

    private OperatingSystem getTargetOsFromUpdate(Key version) {
        ScopedManifestKey scoped = ScopedManifestKey.parse(version);
        if (scoped == null) {
            throw new IllegalStateException("Cannot determin OS from key " + version);
        }

        return scoped.getOperatingSystem();
    }

}
