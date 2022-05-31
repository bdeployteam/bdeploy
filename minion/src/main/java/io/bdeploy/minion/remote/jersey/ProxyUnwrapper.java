package io.bdeploy.minion.remote.jersey;

import io.bdeploy.interfaces.remote.ProxiedResponseWrapper;
import jakarta.ws.rs.core.Response;

@FunctionalInterface
public interface ProxyUnwrapper {

    public Response unwrap(ProxiedResponseWrapper wrapper);

}
