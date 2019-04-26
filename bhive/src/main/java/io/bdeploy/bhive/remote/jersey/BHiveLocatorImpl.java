package io.bdeploy.bhive.remote.jersey;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response.Status;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.ActivityReporter;

/**
 * Uses the server's {@link BHiveRegistry} to find a named {@link BHive} and return it's {@link BHiveRegistry}.
 */
public class BHiveLocatorImpl implements BHiveLocator {

    @Inject
    private BHiveRegistry registry;

    @Inject
    private ActivityReporter reporter;

    @Context
    private ResourceContext rc;

    @Override
    public BHiveResource getNamedHive(String name) {
        BHive hive = registry.get(name);
        if (hive == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        return rc.initResource(new BHiveResourceImpl(hive, reporter));
    }

}
