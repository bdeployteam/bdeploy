package io.bdeploy.jersey;

import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

/**
 * A {@link CLStaticHttpHandler} which *always* sets an aggressive cache control.
 */
public class JerseyCachingCLStaticHttpHandler extends CLStaticHttpHandler {

    public static final String CACHE_AGGRESSIVE = "public, max-age=31536000";

    public JerseyCachingCLStaticHttpHandler(ClassLoader classLoader, String docRoot) {
        super(classLoader, docRoot);
    }

    @Override
    public void service(Request request, Response response) throws Exception {
        // in any case, we want to aggressively cache static resources.
        response.addHeader("Cache-Control", CACHE_AGGRESSIVE);

        super.service(request, response);
    }

}
