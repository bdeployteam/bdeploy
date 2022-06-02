package io.bdeploy.interfaces.remote;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;

public class ProxiedResponseWrapper {

    public static final String COOKIE_BDPROXY = "BDPROXY_";

    /**
     * Wraps information about a single cookies sent along with a request.
     */
    public static final class ProxiedResonseCookie extends ProxiedRequestWrapper.ProxiedRequestCookie {

        public String comment;
        public int maxAge;
        public Date expiry;
        public boolean secure;
        public boolean httpOnly;

        @JsonCreator
        public ProxiedResonseCookie(@JsonProperty("name") String name, @JsonProperty("value") String value,
                @JsonProperty("version") int version, @JsonProperty("path") String path, @JsonProperty("domain") String domain,
                @JsonProperty("comment") String comment, @JsonProperty("maxAge") int maxAge, @JsonProperty("expiry") Date expiry,
                @JsonProperty("secure") boolean secure, @JsonProperty("httpOnly") boolean httpOnly) {
            super(name, value, version, path, domain);

            this.comment = comment;
            this.maxAge = maxAge;
            this.expiry = expiry;
            this.secure = secure;
            this.httpOnly = httpOnly;
        }

    }

    public int responseCode;
    public String responseReason;

    public Map<String, List<String>> headers;
    public Map<String, ProxiedResonseCookie> cookies;

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
        if (cookies != null) {
            for (Map.Entry<String, ProxiedResonseCookie> entry : cookies.entrySet()) {
                ProxiedResonseCookie c = entry.getValue();
                builder.cookie(new NewCookie.Builder(COOKIE_BDPROXY + entry.getKey()).value(c.value).version(c.version)
                        .path(c.path).domain(c.domain).comment(c.comment).maxAge(c.maxAge).expiry(c.expiry).secure(c.secure)
                        .httpOnly(c.httpOnly).build());
            }
        }
        return builder.build();
    }

}
