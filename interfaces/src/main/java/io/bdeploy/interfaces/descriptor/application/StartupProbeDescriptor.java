package io.bdeploy.interfaces.descriptor.application;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class StartupProbeDescriptor {

    @JsonPropertyDescription("The ID of a defined HTTP endpoint of type 'PROBE_STARTUP' which to query. The application is considered started once this endpoint returns a status of 200.")
    public String endpoint;

}
