package io.bdeploy.interfaces.remote;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;

public class ProxiedRequestWrapper {

    public String group;
    public String instanceId;
    public String applicationId;

    public String method;
    public HttpEndpoint endpoint;

    public String subPath;
    public Map<String, List<String>> headers;
    public Map<String, List<String>> queryParameters;

    // TODO: cookies!

    public String base64body;
    public String bodyType;

    @JsonCreator
    public ProxiedRequestWrapper(@JsonProperty("group") String group, @JsonProperty("instanceId") String instanceId,
            @JsonProperty("applicationId") String applicationId, @JsonProperty("method") String method,
            @JsonProperty("endpoint") HttpEndpoint endpoint, @JsonProperty("subPath") String subPath,
            @JsonProperty("headers") Map<String, List<String>> headers,
            @JsonProperty("queryParameters") Map<String, List<String>> queryParameters,
            @JsonProperty("base64body") String base64body, @JsonProperty("bodyType") String bodyType) {
        this.group = group;
        this.instanceId = instanceId;
        this.applicationId = applicationId;
        this.method = method;
        this.endpoint = endpoint;
        this.subPath = subPath;
        this.headers = headers;
        this.queryParameters = queryParameters;
        this.base64body = base64body;
        this.bodyType = bodyType;
    }

}
