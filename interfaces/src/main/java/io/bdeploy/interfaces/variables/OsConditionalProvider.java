package io.bdeploy.interfaces.variables;

import java.util.function.Function;

import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;

/**
 * Conditionally output text based on the current os. E.g. ${LINUX:somevalue} will only expand if the OS is linux.
 */
public class OsConditionalProvider implements Function<String, String> {

    @Override
    public String apply(String t) {
        int colon = t.indexOf(':');
        String os = t.substring(0, colon);
        try {
            OperatingSystem v = OperatingSystem.valueOf(os);
            if (OsHelper.getRunningOs() == v) {
                return t.substring(colon + 1);
            } else {
                return "";
            }
        } catch (Exception e) {
            return null; // not an OS conditional, unsupported OS, etc.
        }
    }

}
