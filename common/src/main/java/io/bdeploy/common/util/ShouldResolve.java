package io.bdeploy.common.util;

import java.util.function.Function;

/**
 * A function that will be called to determine whether or not the resolving of a given parameter value should be executed.
 */
public interface ShouldResolve extends Function<String, Boolean> {

}
