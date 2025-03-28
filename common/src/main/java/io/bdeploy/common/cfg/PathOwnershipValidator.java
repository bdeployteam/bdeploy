package io.bdeploy.common.cfg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.UserPrincipal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.cfg.Configuration.ConfigValidator;
import io.bdeploy.common.cfg.Configuration.ValidationMessage;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;

/**
 * Checks if the current user has access to the given {@link Path}.
 */
@ValidationMessage("The given root directory does not belong to the current user: %s")
public class PathOwnershipValidator implements ConfigValidator<String> {

    private static final Logger log = LoggerFactory.getLogger(PathOwnershipValidator.class);

    @Override
    public boolean test(String value) {
        if (OsHelper.getRunningOs() == OperatingSystem.WINDOWS) {
            return true; // Don't check on windows, as windows does not suffer the problem as hard.
        }

        Path target = Paths.get(value);
        if (!PathHelper.exists(target)) {
            return true; // OK, the user may be able to create the directory!
        }

        try {
            UserPrincipal current = target.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
            UserPrincipal owner = Files.getOwner(target);

            boolean ok = owner.getName().equals(current.getName());

            if (!ok) {
                log.warn("Path ownership validation failed: user={}, owner={}", current, owner);
            }

            return ok;
        } catch (UnsupportedOperationException ex) {
            if (log.isDebugEnabled()) {
                log.debug("Checking directory ownership not possible", ex);
            }
            return true; // OK, we can't check on this filesystem as it does not know the concept of an 'owner'.
        } catch (IOException e) {
            log.warn("Cannot check directory ownership of {}", target, e);
            return false; // Not OK, something is too wrong to validate.
        }
    }
}
