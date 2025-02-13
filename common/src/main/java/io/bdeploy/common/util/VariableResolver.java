package io.bdeploy.common.util;

import java.util.function.UnaryOperator;

/**
 * Type-safe interface for all variable resolvers.
 */
public interface VariableResolver extends UnaryOperator<String> {

    /**
     * Resolves the given parameter and returns the expanded value. Return <code>null</code> to signal that the current
     * {@link VariableResolver} cannot handle the value.
     */
    @Override
    String apply(String s);
}
