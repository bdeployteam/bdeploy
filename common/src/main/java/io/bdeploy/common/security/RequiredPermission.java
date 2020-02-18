package io.bdeploy.common.security;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.bdeploy.common.security.ScopedPermission.Permission;

/**
 * Specifies the permission required to access methods.
 * <p>
 * This annotation can be specified on a class or on method(s). Specifying it
 * at a class level means that it applies to all the methods in the class.
 * Specifying it on a method means that it is applicable to that method only.
 * If applied at both the class and methods level, the method value overrides
 * the class value.
 * </p>
 */
@Documented
@Retention(RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface RequiredPermission {

    /**
     * The permission required to access the method
     */
    public Permission permission();

    /**
     * The name of the query parameter / path parameter containing the actual value. The returned value
     * can be used to obtain the value from the actual request URI.
     */
    public String scope() default "";

}
