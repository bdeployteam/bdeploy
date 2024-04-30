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
     * @return <code>true</code> for <i>very verbose</i> (= with activity reporting), <code>false</code> for <i>verbose</i> (=
     *         without activity reporting)
     */
    boolean value() default false;
}
