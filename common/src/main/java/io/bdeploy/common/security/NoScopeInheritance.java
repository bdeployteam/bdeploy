package io.bdeploy.common.security;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Used together with the {@link RequiredPermission} annotation. It will stop searching for parent scopes in the inheritance chain
 * of the given method.
 */
@Documented
@Retention(RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface NoScopeInheritance {

}
