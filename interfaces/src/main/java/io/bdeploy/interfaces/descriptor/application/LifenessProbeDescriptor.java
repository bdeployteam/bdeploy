package io.bdeploy.interfaces.descriptor.application;

public class LifenessProbeDescriptor {

    /**
     * The endpoint to query. Once the endpoint responds with code 200, the process is considered RUNNING.
     * <p>
     * The endpoint must be defined on the process using the PROBE_ALIVE type.
     */
    public String endpoint;

    /**
     * The initial delay after starting the container before beginning with lifeness queries.
     * <p>
     * The initial delay begins after the process is considered RUNNING. This can be either instantly after starting the process
     * (not startup probe configure), or after the configured startup probe considers the process RUNNING.
     */
    public long initialDelaySeconds;

    /**
     * The interval in which the process is queried whether it is still alive. Once a process becomes "not alive" it receives the
     * according status, which is shown in the UI.
     */
    public long periodSeconds;

}
