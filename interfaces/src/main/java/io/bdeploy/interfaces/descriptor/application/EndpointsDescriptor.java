package io.bdeploy.interfaces.descriptor.application;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class EndpointsDescriptor {

    @JsonPropertyDescription("HTTP based endpoints")
    public List<HttpEndpoint> http = new ArrayList<>();

}
