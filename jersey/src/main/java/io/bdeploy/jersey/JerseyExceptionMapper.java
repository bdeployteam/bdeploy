package io.bdeploy.jersey;

import java.lang.reflect.InvocationTargetException;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.ActivityReporter.ActivityCancelledException;

@Provider
public class JerseyExceptionMapper implements ExceptionMapper<RuntimeException> {

    private static final Logger log = LoggerFactory.getLogger(JerseyExceptionMapper.class);

    @Override
    public Response toResponse(RuntimeException exception) {
        if (exception instanceof WebApplicationException && (exception.getCause() == null || exception.getCause() == exception)) {
            WebApplicationException webEx = (WebApplicationException) exception;
            return Response.status(webEx.getResponse().getStatus(), webEx.getMessage()).build();
        }

        if (hasCancelException(exception)) {
            return Response.status(444, "Operation cancelled by user.").build();
        }

        int code = Status.INTERNAL_SERVER_ERROR.getStatusCode();
        if (!(exception instanceof WebApplicationException)) {
            log.warn("Unmapped Exception", exception);
        } else {
            code = ((WebApplicationException) exception).getResponse().getStatus();
        }

        // a little hacky: provide the exception string representations as reason.
        return Response.status(code, mapExceptionCausesToReason(exception)).build();
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

    private String mapExceptionCausesToReason(RuntimeException exception) {
        if (exception == null) {
            return "<unknown>";
        }

        StringBuilder reason = new StringBuilder();
        Throwable current = exception;
        do {
            reason.append(current.toString());
            reason.append(" // ");
            if (current instanceof InvocationTargetException) {
                current = ((InvocationTargetException) current).getTargetException();
            } else {
                if (current == current.getCause()) {
                    break;
                }
                current = current.getCause();
            }
        } while (current != null);
        return reason.toString();
    }

}
