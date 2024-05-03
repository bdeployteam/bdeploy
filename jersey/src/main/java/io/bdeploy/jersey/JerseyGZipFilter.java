package io.bdeploy.jersey;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.spi.ContentEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;

/**
 * Client filter adding support for {@link org.glassfish.jersey.spi.ContentEncoder content encoding}. The filter adds
 * list of supported encodings to the Accept-Header values.
 * Supported encodings are determined by looking
 * up all the {@link org.glassfish.jersey.spi.ContentEncoder} implementations registered in the corresponding
 * {@link ClientConfig client configuration}.
 * <p>
 * Based on org.glassfish.jersey.client.filter.EncodingFilter which suffers from problems when used together
 * with multipart feature (it doesn't work).
 */
public final class JerseyGZipFilter implements ClientRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JerseyGZipFilter.class);
    private static final String GZIP_ENCODING = "gzip";

    @Inject
    private InjectionManager injectionManager;
    private volatile List<Object> supportedEncodings = null;

    @Override
    public void filter(ClientRequestContext request) throws IOException {
        if (getSupportedEncodings().isEmpty()) {
            return;
        }

        request.getHeaders().addAll(HttpHeaders.ACCEPT_ENCODING, getSupportedEncodings());

        if (!getSupportedEncodings().contains(GZIP_ENCODING)) {
            log.warn("GZIP encoding not supported, supported encodings: {}", getSupportedEncodings());
        } else {
            // don't add Content-Encoding header for requests with no entity and for requests which use multipart
            if (request.hasEntity() && !request.getHeaderString(HttpHeaders.CONTENT_TYPE).contains("multipart")) {
                if (request.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING) == null) {
                    // FIXME: this does not work for older BDeploy server versions :| need to be switchable in the future.
                    //                    request.getHeaders().putSingle(HttpHeaders.CONTENT_ENCODING, GZIP_ENCODING);
                }
            }
        }
    }

    List<Object> getSupportedEncodings() {
        // no need for synchronization - in case of a race condition, the property
        // may be set twice, but it does not break anything
        if (supportedEncodings == null) {
            SortedSet<String> se = new TreeSet<>();
            List<ContentEncoder> encoders = injectionManager.getAllInstances(ContentEncoder.class);
            for (ContentEncoder encoder : encoders) {
                se.addAll(encoder.getSupportedEncodings());
            }
            supportedEncodings = new ArrayList<>(se);
        }
        return supportedEncodings;
    }
}
