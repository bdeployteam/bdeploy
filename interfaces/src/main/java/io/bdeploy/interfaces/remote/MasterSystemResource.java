package io.bdeploy.interfaces.remote;

import java.util.Map;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.system.SystemConfiguration;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Manages systems on the master.
 */
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface MasterSystemResource {

    /**
     * @return all existing systems in their latest/current version
     */
    @GET
    public Map<Manifest.Key, SystemConfiguration> list();

    /**
     * @param system the updated or new system
     */
    @POST
    public Manifest.Key update(SystemConfiguration system);

    /**
     * @param id the system to delete
     */
    @DELETE
    public void delete(@QueryParam("id") String id);

}
