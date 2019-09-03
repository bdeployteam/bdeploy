package io.bdeploy.common.cli;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * The annotated tool will be run in verbose mode by default.
 */
@Documented
@Retention(RUNTIME)
@Target({ TYPE })
public @interface ToolDefaultVerbose {

    /**
     * @return whether to turn on activity reporting as well.
     */
    boolean value() default false;
}
