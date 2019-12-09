package io.bdeploy.interfaces.variables;

import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.VariableResolver;

/**
 * Conditionally output text based on the current os. E.g. {{LINUX:somevalue}} will only expand if the OS is linux.
 */
public class OsVariableResolver implements VariableResolver {

    @Override
    public String apply(String t) {
        for (OperatingSystem os : OperatingSystem.values()) {
            String pattern = os.name() + ":";
            if (!t.startsWith(pattern)) {
                continue;
            }
            // Return an empty string to end signal we resolved the variable
            if (OsHelper.getRunningOs() != os) {
                return "";
            }
            return t.substring(pattern.length());
        }

        // Not an OS condition
        return null;
    }

}
