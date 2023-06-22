package io.bdeploy.jersey;

import java.io.IOException;

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.server.AddOn;
import org.glassfish.grizzly.http.server.HttpServerFilter;
import org.glassfish.grizzly.http.server.NetworkListener;

public class JerseyCspFilter extends BaseFilter {

    private static final String CSP_HDR = "Content-Security-Policy";

    // @formatter:off
    private static final String[] CSP_OPTS = {
            "default-src 'none'",
            "frame-ancestors 'self'",
            "frame-src 'self'",
            "font-src 'self'",
            "script-src 'self' 'unsafe-inline'",
            "connect-src 'self'",
            "img-src 'self' data:",
            "style-src 'self' 'unsafe-inline'",
            "base-uri 'self'",
            "form-action 'self'"
    };
    // @formatter:on

    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        Object msg = ctx.getMessage();
        if (msg instanceof HttpContent) {
            HttpContent resp = (HttpContent) msg;

            if (resp.getHttpHeader() != null && resp.getHttpHeader().getHeader(CSP_HDR) == null) {
                resp.getHttpHeader().addHeader(CSP_HDR, String.join("; ", CSP_OPTS));
                resp.getHttpHeader().addHeader("X-Frame-Options", "DENY"); // legacy browsers.
            }
        }

        return ctx.getInvokeAction();
    }

    final static class JerseyCspAddOn implements AddOn {

        @Override
        public void setup(NetworkListener networkListener, FilterChainBuilder builder) {
            final int httpServerFilterIdx = builder.indexOfType(HttpServerFilter.class);

            // need to insert the filter before the http server, as it would otherwise never be asked to handle anything.
            if (httpServerFilterIdx >= 0) {
                builder.add(httpServerFilterIdx, new JerseyCspFilter());
            }
        }

    }

}
