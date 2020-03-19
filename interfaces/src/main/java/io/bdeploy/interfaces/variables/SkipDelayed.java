package io.bdeploy.interfaces.variables;

import io.bdeploy.common.util.ShouldResolve;

/**
 * A function that skips resolving variables marked with the {@linkplain Variables#DELAYED delay} prefix.
 */
public class SkipDelayed implements ShouldResolve {

    @Override
    public Boolean apply(String t) {
        return !t.startsWith(Variables.DELAYED.name());
    }

}
