package io.bdeploy.jersey;

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpContext;
import org.glassfish.grizzly.http.server.AddOn;
import org.glassfish.grizzly.http.server.FileCacheFilter;
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
            "script-src 'self' 'unsafe-inline' 'unsafe-eval'",
            "connect-src 'self'",
            "img-src 'self' https://* data:",
            "style-src 'self' 'unsafe-inline'",
            "base-uri 'self'",
            "form-action 'self'",
            "worker-src 'self' blob:"
    };
    // @formatter:on

    @Override
    public NextAction handleWrite(FilterChainContext ctx) {
        Object msg = ctx.getMessage();
        if (msg instanceof HttpContent) {
            HttpContent resp = (HttpContent) msg;
            HttpContext c = HttpContext.get(ctx);

            boolean needHdrs = resp.getHttpHeader() != null && resp.getHttpHeader().getHeader(CSP_HDR) == null;

            // user interface proxy requests should not add those headers.
            String uri = c.getRequest().getRequestURI();
            if (uri.contains("/upx/") || "/api/proxy".equals(uri)) {
                needHdrs = false;
            }

            if (needHdrs) {
                resp.getHttpHeader().addHeader(CSP_HDR, String.join("; ", CSP_OPTS));

                resp.getHttpHeader().addHeader("X-Frame-Options", "DENY"); // legacy browsers.
                resp.getHttpHeader().addHeader("X-Content-Type-Options", "nosniff"); // prevent type sniffing.
            }
        }

        return ctx.getInvokeAction();
    }

    static final class JerseyCspAddOn implements AddOn {

        @Override
        public void setup(NetworkListener networkListener, FilterChainBuilder builder) {
            final int httpServerFilterIdx = builder.indexOfType(HttpServerFilter.class);
            final int cacheFilterIdx = builder.indexOfType(FileCacheFilter.class);

            final int insertIdx = Math.max(0, Math.min(httpServerFilterIdx, cacheFilterIdx));

            // the filter needs to be in the correct position to always hit. this needs
            // to be 1) before the actual http handlers, and 2) even before the handler
            // which handles traffic out of the file cache - these requests *also* need
            // our headers added.
            builder.add(insertIdx, new JerseyCspFilter());
        }

    }

}
