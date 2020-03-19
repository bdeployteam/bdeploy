package io.bdeploy.common.cfg;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.bdeploy.common.cfg.Configuration.ConfigValidator;
import io.bdeploy.common.cfg.Configuration.ValidationMessage;

@ValidationMessage("Path is not an initialized BDeploy root directory, run 'bdeploy init': %s")
public class MinionRootValidator implements ConfigValidator<String> {

    @Override
    public boolean validate(String value) {
        Path target = Paths.get(value);
        if (!Files.isDirectory(target)) {
            return false;
        }

        return Files.isRegularFile(target.resolve("etc/state.json"));
    }

}
