package io.bdeploy.common.cfg;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.bdeploy.common.cfg.Configuration.ConfigValidator;
import io.bdeploy.common.cfg.Configuration.ValidationMessage;
import io.bdeploy.common.util.PathHelper;

/**
 * Checks if the given {@link Path} does not exist.
 */
@ValidationMessage("Path exists, but should not exist: %s")
public class NonExistingPathValidator implements ConfigValidator<String> {

    @Override
    public boolean test(String value) {
        return !PathHelper.exists(Paths.get(value));
    }
}
