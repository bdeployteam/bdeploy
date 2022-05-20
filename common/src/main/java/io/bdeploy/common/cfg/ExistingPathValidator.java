package io.bdeploy.common.cfg;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.bdeploy.common.cfg.Configuration.ConfigValidator;
import io.bdeploy.common.cfg.Configuration.ValidationMessage;
import io.bdeploy.common.util.PathHelper;

@ValidationMessage("Path does not exist, but should exist: %s")
public class ExistingPathValidator implements ConfigValidator<String> {

    @Override
    public boolean validate(String value) {
        Path p = Paths.get(value);
        return PathHelper.exists(p);
    }

}
