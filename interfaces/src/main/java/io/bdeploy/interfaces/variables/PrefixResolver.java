package io.bdeploy.interfaces.variables;

import io.bdeploy.common.util.VariableResolver;

/**
 * Base class for all resolvers that replace variables with their actual value. Also handles arithmetic calculations for
 * {@link Variables} which support them.
 */
public abstract class PrefixResolver implements VariableResolver {

    protected final Variables prefix;

    /**
     * Creates a new resolver for the given prefix.
     *
     * @param prefix the prefix resolved by this resolver
     */
    protected PrefixResolver(Variables prefix) {
        this.prefix = prefix;
    }

    @Override
    public String apply(String s) {
        String prefixString = prefix.getPrefix();
        if (!s.startsWith(prefixString)) {
            return null;
        }

        String expr = s.substring(prefixString.length());

        if (prefix.isArithmeticAllowed()) {
            // Even if arithmetics are enabled, they are only applicable if there is at least 1 colon and both parts can be parsed to long
            int lastColonIndex = expr.lastIndexOf(':');
            if (lastColonIndex != -1) {
                Long value2 = parseLong(expr.substring(lastColonIndex + 1));
                if (value2 != null) {
                    Long value1 = parseLong(doResolve(expr.substring(0, lastColonIndex))); //TODO figure out how to deal with non-numeric expansion out of doResolve
                    if (value1 != null) {
                        return Long.toString(value1 + value2);
                    }
                }
            }
        }

        return doResolve(expr);
    }

    private static Long parseLong(String str) {
        try {
            return Long.valueOf(str);
        } catch (NumberFormatException ignore) {
            return null;
        }
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
