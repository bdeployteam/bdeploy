package io.bdeploy.interfaces.variables;

import io.bdeploy.common.util.VariableResolver;

/**
 * used for all expansions not required to determine which system variables are required.
 */
public final class EmptyVariableResolver implements VariableResolver {

    @Override
    public String apply(String t) {
        return ""; // always resolve to *something* (non-null);
    }

}
