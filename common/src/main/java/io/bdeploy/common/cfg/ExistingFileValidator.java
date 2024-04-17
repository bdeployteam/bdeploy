package io.bdeploy.common.cfg;

import java.io.File;
import java.nio.file.Path;

import io.bdeploy.common.cfg.Configuration.ValidationMessage;

/**
 * Checks if the given {@link Path} exists and {@link File#isFile() is a file}.
 */
@ValidationMessage("File does not exist, but should exist: %s")
public class ExistingFileValidator extends ExistingPathValidator {

    @Override
    public boolean validate(String value) {
        return super.validate(value) ? p.toFile().isFile() : false;
    }
}
