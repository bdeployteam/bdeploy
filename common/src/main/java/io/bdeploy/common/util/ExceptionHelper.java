package io.bdeploy.common.util;

import java.lang.reflect.InvocationTargetException;

public class ExceptionHelper {

    private ExceptionHelper() {
    }

    public static String mapExceptionCausesToReason(Throwable exception) {
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
