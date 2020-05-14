package io.bdeploy.interfaces.remote;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.directory.InstanceDirectoryEntry;

@Path("/processes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SlaveProcessResource {

    /**
     * Starts all applications of an instance having the start type 'INSTANCE' configured
     *
     * @param instanceId
     *            the unique id of the instance.
     */
    @POST
    @Path("/startAll")
    public void start(@QueryParam("u") String instanceId);

    /**
     * Starts a single application of an instance.
     *
     * @param instanceId
     *            the unique id of the instance.
     * @param applicationId
     *            the unique ID of the application.
     */
    @POST
    @Path("/startApp")
    public void start(@QueryParam("u") String instanceId, @QueryParam("a") String applicationId);

    /**
     * Stops a single application of an instance.
     *
     * @param instanceId
     *            the unique id of the instance.
     * @param applicationId
     *            the unique ID of the application.
     */
    @POST
    @Path("/stopApp")
    public void stop(@QueryParam("u") String instanceId, @QueryParam("a") String applicationId);

    /**
     * Stops all applications of an instance.
     *
     * @param instanceId
     *            the unique id of the instance.
     */
    @POST
    @Path("/stopAll")
    public void stop(@QueryParam("u") String instanceId);

    /**
     * Returns status information about an instance.
     *
     * @param instanceId
     *            the unique id of the instance.
     * @return the status information
     */
    @GET
    @Path("/status")
    public InstanceNodeStatusDto getStatus(@QueryParam("u") String instanceId);

    /**
     * @param instanceId the instance UUID
     * @param tag the tag for which to retrieve the output file entry.
     * @param applicationId the application ID for which to retrieve the output file entry.
     * @return an {@link InstanceDirectoryEntry}, can be used with
     *         {@link SlaveDeploymentResource#getEntryContent(InstanceDirectoryEntry, long, long)}.
     */
    @GET
    @Path("/output")
    public InstanceDirectoryEntry getOutputEntry(@QueryParam("u") String instanceId, @QueryParam("t") String tag,
            @QueryParam("a") String applicationId);

    /**
     * Writes data to the stdin stream of an application.
     *
     * @param instanceId
     *            the unique id of the instance.
     * @param applicationId
     *            the unique ID of the application.
     * @param data
     *            the data to write to stdin of the application.
     */
    @POST
    @Path("/stdin")
    public void writeToStdin(@QueryParam("u") String instanceId, @QueryParam("a") String applicationId, String data);

}
