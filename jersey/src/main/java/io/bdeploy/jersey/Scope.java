package io.bdeploy.jersey;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates a parameter which contributes to the scope of actions and operations started within the current request.
 */
@Documented
@Retention(RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Scope {

}
