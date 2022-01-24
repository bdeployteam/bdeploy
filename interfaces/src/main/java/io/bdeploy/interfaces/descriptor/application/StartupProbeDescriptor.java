package io.bdeploy.interfaces.descriptor.application;

public class StartupProbeDescriptor {

    /**
     * The endpoint to query. Once the endpoint responds with code 200, the process is considered RUNNING.
     * <p>
     * The endpoint must be defined on the process using the PROBE_STARTUP type.
     */
    public String endpoint;

}
