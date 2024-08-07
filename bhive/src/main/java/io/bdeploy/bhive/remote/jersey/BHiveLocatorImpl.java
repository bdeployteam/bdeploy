package io.bdeploy.bhive.remote.jersey;

import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.jersey.fs.FileSystemSpaceService;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response.Status;

/**
 * Uses the server's {@link BHiveRegistry} to find a named {@link BHive} and return it's {@link BHiveRegistry}.
 */
public class BHiveLocatorImpl implements BHiveLocator {

    private static final Logger log = LoggerFactory.getLogger(BHiveLocatorImpl.class);

    @Inject
    private BHiveRegistry registry;

    @Inject
    private FileSystemSpaceService fsss;

    @Context
    private ResourceContext rc;

    @Context
    private ContainerRequestContext context;

    @Override
    public BHiveResource getNamedHive(String name) {
        BHive hive = registry.get(name);
        if (hive == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        try {
            // Need to check this here to avoid accepting a large upload if the filesystem doesn't have enough space.
            // This essentially locks down all remote communication with a BHive until there is enough free space.
            Path path = Path.of(hive.getUri());
            if (!fsss.hasFreeSpace(path)) {
                throw new WebApplicationException("Not enough free space in " + path, Status.SERVICE_UNAVAILABLE);
            }
        } catch (IllegalArgumentException | FileSystemNotFoundException | SecurityException e) {
            log.warn("Cannot check free space on {}", hive.getUri(), e);
        }
        return rc.initResource(new BHiveResourceImpl(hive));
    }

    @Override
    public Permission getRequiredPermission(String name) {
        BHive hive = registry.get(name);
        if (hive == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        return registry.getRequiredPermission();
    }
}
