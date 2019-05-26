package io.bdeploy.pcu;

/**
 * Exception thrown by the PCU indicating that something unexpected happened.
 */
public class PcuRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 8719603524182336588L;

    /**
     * Constructs a new runtime exception with the specified detail message.
     */
    public PcuRuntimeException(String message) {
        super(message);
    }

    /**
     * Constructs a new runtime exception with the specified detail message and cause.
     */
    public PcuRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

}
