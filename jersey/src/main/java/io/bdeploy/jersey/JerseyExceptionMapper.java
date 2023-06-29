package io.bdeploy.jersey;

import java.lang.reflect.InvocationTargetException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import io.bdeploy.common.ActivityReporter.ActivityCancelledException;
import io.bdeploy.common.util.ExceptionHelper;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class JerseyExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger log = LoggerFactory.getLogger(JerseyExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof JsonMappingException || exception instanceof JsonParseException) {
            log.warn("Internal JSON Mapping Exception", exception);
            exception = new WebApplicationException("JSON processing error, see logs");
        }

        if (exception instanceof WebApplicationException && (exception.getCause() == null || exception.getCause() == exception)) {
            WebApplicationException webEx = (WebApplicationException) exception;

            if (webEx.getResponse().getStatus() == Status.TEMPORARY_REDIRECT.getStatusCode()) {
                // response carries valuable headers.
                return webEx.getResponse();
            }

            return Response.status(webEx.getResponse().getStatus(), webEx.getMessage()).build();
        }

        if (hasCancelException(exception)) {
            return Response.status(444, "Operation cancelled by user.").build();
        }

        int code = Status.INTERNAL_SERVER_ERROR.getStatusCode();
        if (!(exception instanceof WebApplicationException)) {
            log.warn("Unmapped Exception", exception);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Server Exception", exception);
            }
            code = ((WebApplicationException) exception).getResponse().getStatus();
        }

        // a little hacky: provide the exception string representations as reason.
        return Response.status(code, ExceptionHelper.mapExceptionCausesToReason(exception)).build();
    }

    private boolean hasCancelException(Throwable e) {
        while (e != null) {
            if (e instanceof InvocationTargetException) {
                e = ((InvocationTargetException) e).getTargetException();
            }

            if (e instanceof ActivityCancelledException) {
                return true;
            }

            Throwable parent = e.getCause();
            if (parent == null || parent == e) {
                return false;
            }

            e = parent;
        }
        return false;
    }

}
