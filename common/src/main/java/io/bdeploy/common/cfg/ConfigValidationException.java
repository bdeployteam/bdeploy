package io.bdeploy.common.cfg;

public class ConfigValidationException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public ConfigValidationException() {
        super("Validation Issues Detected");
    }

}
