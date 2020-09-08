package io.bdeploy.jersey.activity;

import java.io.IOException;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Providers;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;

public class JerseyClientActivityFilter implements ClientRequestFilter, ClientResponseFilter {

    private static final String ACT = "__ACTIVITY__";

    @Context
    private Providers providers;

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        ActivityReporter reporter = providers.getContextResolver(ActivityReporter.class, MediaType.WILDCARD_TYPE)
                .getContext(ActivityReporter.class);
        requestContext.setProperty(ACT,
                reporter.start("Request to " + requestContext.getUri().getHost() + ": " + requestContext.getUri().getPath()));
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        Activity act = (Activity) requestContext.getProperty(ACT);
        if (act != null) {
            act.close();
        }
    }

}
