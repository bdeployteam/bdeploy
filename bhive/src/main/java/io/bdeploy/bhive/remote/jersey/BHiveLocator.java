package io.bdeploy.bhive.remote.jersey;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;

/**
 * Root resource location for all hives.
 * <p>
 * Since a single server can always serve multiple {@link BHive}s, this resource handles routing to the correct
 * {@link BHiveResource}.
 *
 * @see JerseyRemoteBHive#DEFAULT_NAME
 */
@Path("/hives")
public interface BHiveLocator {

    /**
     * @param name the name of the {@link BHive} to retrieve the {@link BHiveResource} for.
     * @return the {@link BHiveResource} which can be used to actually access the named {@link BHive}.
     */
    @Path("{hive}")
    @RequiredPermission(scope = "hive", permission = Permission.READ, dynamicPermission = "getRequiredPermission")
    public BHiveResource getNamedHive(@PathParam("hive") String name);

    /**
     * @param name the name of the hive to check
     * @return the minimum required permission to access this hive.
     */
    public Permission getRequiredPermission(String name);

}
