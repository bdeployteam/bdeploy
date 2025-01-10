package io.bdeploy.gradle;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutionException;

import jakarta.ws.rs.ProcessingException;

class GradleExceptionHelper {

    static String mapExceptionCausesToReasonWithNewline(Throwable exception) {
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
                reason.append("\n * " + message);
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
        } while (current != null);
        return reason.toString();
    }

    private static boolean isIgnorableExceptionType(Throwable current) {
        // add more if required.
        return current instanceof ProcessingException || current instanceof ExecutionException;
    }
}
