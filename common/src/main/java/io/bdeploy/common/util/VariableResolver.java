package io.bdeploy.common.util;

import java.util.function.UnaryOperator;

/**
 * Type-safe interface for all variable resolvers.
 */
public interface VariableResolver extends UnaryOperator<String> {

}
