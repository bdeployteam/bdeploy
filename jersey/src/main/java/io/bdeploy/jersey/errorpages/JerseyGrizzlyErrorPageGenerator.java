package io.bdeploy.jersey.errorpages;

import org.glassfish.grizzly.http.server.ErrorPageGenerator;
import org.glassfish.grizzly.http.server.Request;

public class JerseyGrizzlyErrorPageGenerator implements ErrorPageGenerator {

    @Override
    public String generate(Request request, int status, String reasonPhrase, String description, Throwable exception) {
        return JerseyCustomErrorPages.getErrorHtml(status, reasonPhrase);
    }

}
