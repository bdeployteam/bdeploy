package io.bdeploy.minion.remote.jersey;

import io.bdeploy.interfaces.remote.ProxiedRequestWrapper;
import io.bdeploy.interfaces.remote.ProxiedResponseWrapper;

@FunctionalInterface
public interface ProxyForwarder {

    ProxiedResponseWrapper forward(ProxiedRequestWrapper request);

}
