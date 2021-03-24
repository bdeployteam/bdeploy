package io.bdeploy.common.util;

import java.lang.reflect.InvocationTargetException;

import jakarta.ws.rs.ProcessingException;

public class ExceptionHelper {

    private ExceptionHelper() {
    }

    public static String mapExceptionCausesToReason(Throwable exception) {
        if (exception == null) {
            return "<unknown>";
        }

        StringBuilder reason = new StringBuilder();
        Throwable current = exception;
        String last = null;
        do {
            String message = current.getMessage();
            boolean ignore = isIgnorableExceptionType(current) || message == null || message.equals(last);
            if (!ignore) {
                reason.append(message);
            }
            last = message;
            if (current instanceof InvocationTargetException) {
                current = ((InvocationTargetException) current).getTargetException();
            } else {
                if (current == current.getCause()) {
                    break;
                }
                current = current.getCause();
            }
            if (current != null && !ignore) {
                reason.append("; ");
            }
        } while (current != null);
        return reason.toString();
    }

    private static boolean isIgnorableExceptionType(Throwable current) {
        // add more if required.
        return current instanceof ProcessingException;
    }

}
