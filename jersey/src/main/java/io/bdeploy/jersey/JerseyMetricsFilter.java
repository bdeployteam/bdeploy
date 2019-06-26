package io.bdeploy.jersey;

import java.io.IOException;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Timer;

import io.bdeploy.common.metrics.Metrics;
import io.bdeploy.common.metrics.Metrics.MetricGroup;
import io.bdeploy.jersey.resources.JerseyMetricsResource;

/**
 * Filter which tracks the timings of all requests.
 *
 * @see JerseyMetricsResource
 */
@Provider
public class JerseyMetricsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String TIMER = "rqTimer";
    private static final Logger log = LoggerFactory.getLogger(JerseyMetricsFilter.class);

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String endpoint = requestContext.getUriInfo().getPath();
        requestContext.setProperty(TIMER, Metrics.getMetric(MetricGroup.HTTP).timer(endpoint).time());
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        Timer.Context context = (Timer.Context) requestContext.getProperty(TIMER);
        if (context != null) {
            context.close();
        } else {
            // happens in case of errors in sub-resource locators, e.g. throwing a WAE.
            if (log.isTraceEnabled()) {
                log.trace("No timer running for request: {}", requestContext);
            }
        }
    }

}
