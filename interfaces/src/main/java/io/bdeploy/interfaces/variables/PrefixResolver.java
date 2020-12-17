package io.bdeploy.interfaces.variables;

import io.bdeploy.common.util.VariableResolver;

/**
 * Base class for all resolvers that replace variables with their actual value.
 */
public abstract class PrefixResolver implements VariableResolver {

    protected final Variables prefix;

    /**
     * Creates a new resolvers for the given prefix.
     *
     * @param prefix the prefix resolved by this resolver
     */
    public PrefixResolver(Variables prefix) {
        this.prefix = prefix;
    }

    @Override
    public String apply(String t) {
        if (!t.startsWith(prefix.getPrefix())) {
            return null;
        }
        String variable = t.substring(prefix.getPrefix().length());
        return doResolve(variable);
    }

    /**
     * Resolves the given variable to their actual value.
     *
     * @param variable
     *            the unpacked variable to resolve
     * @return the resolved value
     */
    protected abstract String doResolve(String variable);

}
