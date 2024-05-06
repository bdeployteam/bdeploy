package io.bdeploy.common.cfg;

import java.nio.file.Files;
import java.nio.file.Path;

import io.bdeploy.common.cfg.Configuration.ValidationMessage;

/**
 * Checks if the given {@link Path} exists and contains either the file <code>etc/state.json</code> or
 * <code>etc/state.json.bak</code>.
 */
@ValidationMessage("Path is not an initialized BDeploy root directory, run 'bdeploy init': %s")
public class MinionRootValidator extends ExistingDirectoryValidator {

    @Override
    public boolean validate(String value) {
        return super.validate(value) && (isRegularFile("etc/state.json") || isRegularFile("etc/state.json.bak"));
    }

    private boolean isRegularFile(String s) {
        return Files.isRegularFile(p.resolve(s));
    }
}
