package io.bdeploy.common.cfg;

import io.bdeploy.common.cfg.Configuration.ConfigValidator;
import io.bdeploy.common.cfg.Configuration.ValidationMessage;

/**
 * Checks if the given input is a valid remote reference.
 */
@ValidationMessage("Invalid remote: '%s'. A remote must be a local path or start with 'https://' and end with '/api'")
public class RemoteValidator implements ConfigValidator<String> {

    public static final String API_SUFFIX = "/api";

    @Override
    public boolean validate(String value) {
        String lower = value.toLowerCase();

        // Allow local paths and file URLs
        if (lower.startsWith("/") || lower.startsWith(".") || lower.startsWith("file:") || lower.startsWith("jar:file:")) {
            return true;
        }

        return lower.startsWith("https://") && lower.endsWith(API_SUFFIX);
    }
}
