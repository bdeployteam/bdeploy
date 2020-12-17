package io.bdeploy.common.cli;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * A human readable category for each tool. Tools will be grouped and described in the help output
 */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface ToolCategory {

    String value();
}
