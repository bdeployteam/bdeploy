package io.bdeploy.common.cfg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import io.bdeploy.common.cfg.Configuration.ConfigValidator;
import io.bdeploy.common.cfg.Configuration.ValidationMessage;
import io.bdeploy.common.util.PathHelper;

@ValidationMessage("Path exists, but should not exist (or be an empty directory): %s")
public class NonExistingPathValidator implements ConfigValidator<String> {

    @Override
    public boolean validate(String value) {
        Path p = Paths.get(value);
        if (Files.isDirectory(p)) {
            // Empty directories are OK.
            try (Stream<Path> list = Files.list(p)) {
                return !list.findAny().isPresent();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot determine directory contents: " + p, e);
            }
        }
        return !PathHelper.exists(p);
    }

}
