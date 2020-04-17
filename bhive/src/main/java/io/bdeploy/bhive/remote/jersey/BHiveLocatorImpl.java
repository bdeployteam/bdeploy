package io.bdeploy.bhive.remote.jersey;

import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.jersey.fs.FileSystemSpaceService;

/**
 * Uses the server's {@link BHiveRegistry} to find a named {@link BHive} and return it's {@link BHiveRegistry}.
 */
public class BHiveLocatorImpl implements BHiveLocator {

    private static final Logger log = LoggerFactory.getLogger(BHiveLocatorImpl.class);

    @Inject
    private BHiveRegistry registry;

    @Inject
    private ActivityReporter reporter;

    @Inject
    private FileSystemSpaceService fsss;

    @Context
    private ResourceContext rc;

    @Override
    public BHiveResource getNamedHive(String name) {
        BHive hive = registry.get(name);
        if (hive == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        try {
            // need to check this here to avoid accepting a large upload if the filesystem doesn't have enough space.
            // this essentially locks down all remote communication with a BHive until there is enough free space.
            Path path = Paths.get(hive.getUri());
            if (!fsss.hasFreeSpace(path)) {
                throw new WebApplicationException("Not enough free space in " + path, Status.SERVICE_UNAVAILABLE);
            }
        } catch (IllegalArgumentException | FileSystemNotFoundException | SecurityException e) {
            log.warn("Cannot check free space on {}", hive.getUri());
            if (log.isDebugEnabled()) {
                log.debug("Error:", e);
            }
        }
        return rc.initResource(new BHiveResourceImpl(hive, reporter));
    }

}
