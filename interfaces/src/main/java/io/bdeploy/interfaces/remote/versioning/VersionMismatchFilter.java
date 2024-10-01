package io.bdeploy.interfaces.remote.versioning;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.remote.v1.PublicRootResource;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.jersey.JerseyClientFactory;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
public class VersionMismatchFilter implements ClientResponseFilter {

    public static final int CODE_VERSION_MISMATCH = 499;

    private static final Logger log = LoggerFactory.getLogger(VersionMismatchFilter.class);
    private final JerseyClientFactory factory;

    public VersionMismatchFilter(JerseyClientFactory factory) {
        this.factory = factory;
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        if (responseContext.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
            // *something* was not found. check versions of server.
            String theirVersion;
            try {
                theirVersion = factory.getProxyClient(PublicRootResource.class).getVersion();
            } catch (NotFoundException nfe) {
                log.warn("Server does not implement the version endpoint", nfe);
                return; // version endpoint not supported.
            } catch (Exception e) {
                log.warn("Cannot check remote version on {}", factory, e);
                return; // nothing we can do here.
            }

            String ourVersion = VersionHelper.getVersion().toString();
            if (ourVersion.equals(theirVersion)) {
                return; // version is the same, it's a "normal" 404.
            }
            throw new WebApplicationException("Cannot perform communication with " + requestContext.getUri().getHost()
                    + " (port: " + requestContext.getUri().getPort() + ", path: " + requestContext.getUri().getPath()
                    + "). The endpoint is not supported due to a version mismatch (target version: " + theirVersion
                    + ", our version: " + ourVersion + "). Please update BDeploy.", CODE_VERSION_MISMATCH);
        }
    }
}
