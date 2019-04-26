package io.bdeploy.common;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Meta-Annotation to mark slow (long-running) tests.
 */
@Retention(RUNTIME)
@Target({ TYPE, METHOD })
@Tag("slow")
public @interface SlowTest {
}
