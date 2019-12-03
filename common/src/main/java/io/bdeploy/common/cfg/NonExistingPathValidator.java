package io.bdeploy.common.cfg;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.bdeploy.common.cfg.Configuration.ConfigValidator;
import io.bdeploy.common.cfg.Configuration.ValidationMessage;

@ValidationMessage("Path exists, but should not exist: %s")
public class NonExistingPathValidator implements ConfigValidator<String> {

    @Override
    public boolean validate(String value) {
        Path p = Paths.get(value);
        return !Files.exists(p);
    }

}
