package io.bdeploy.interfaces.remote;

import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;

public class ProxiedResponseWrapper {

    public int responseCode;
    public String responseReason;

    public Map<String, List<String>> headers;

    // TODO: cookies!

    public String base64body;

    /**
     * Default logic to unwrap the response. This creates a response as identical as possible to the original response.
     */
    public Response defaultUnwrap() {
        ResponseBuilder builder = Response.status(responseCode, responseReason);
        if (base64body != null) {
            // no need to set the type, it's in the headers.
            builder.entity(Base64.decodeBase64(base64body));
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (String value : entry.getValue()) {
                builder.header(entry.getKey(), value);
            }
        }
        return builder.build();
    }

}
