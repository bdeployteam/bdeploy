package io.bdeploy.interfaces.descriptor.application;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class LifenessProbeDescriptor {

    /**
     * The endpoint to query. Once the endpoint responds with code 200, the process is considered RUNNING.
     * <p>
     * The endpoint must be defined on the process using the PROBE_ALIVE type.
     */
    @JsonPropertyDescription("The ID of a defined HTTP endpoint of type 'PROBE_ALIVE' which to query. The application is considered alife as long as this endpoint returns a status of 200. Querying will only start once the process is considered 'RUNNING'.")
    public String endpoint;

    /**
     * The initial delay after starting the container before beginning with liveness queries.
     * <p>
     * The initial delay begins after the process is considered RUNNING. This can be either instantly after starting the process
     * (not startup probe configure), or after the configured startup probe considers the process RUNNING.
     */
    @JsonPropertyDescription("An initial delay in seconds to wait before starting to query this probe. The timer starts after the process has been considered 'RUNNING'.")
    public long initialDelaySeconds;

    /**
     * The interval in which the process is queried whether it is still alive. Once a process becomes "not alive" it receives the
     * according status, which is shown in the UI.
     */
    @JsonPropertyDescription("The interval with which to query this probe.")
    public long periodSeconds;

}
