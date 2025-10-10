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
 * This annotation can be specified on a type or on method(s). Specifying it
 * at type level means that it applies to all the methods in the type.
 * Specifying it on a method means that it is applicable to that method only.
 * If applied at both the type and methods level, the method value overrides
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

    /**
     * Whether the scope is optional. This can be used when the annotation is placed on the interface,
     * and there are methods with and without the scope parameter.
     */
    public boolean scopeOptional() default false;

    /**
     * References a method on the resource which accepts the scope as string, and returns the required minimum permission on that
     * scope.
     * <p>
     * The referenced method will be called during checks for required permissions on the annotated object, using the value of the
     * parameter denoted by the scope parameter and is expected to return an object of type {@link Permission} or
     * <code>null</code> if no permission is required.
     */
    public String dynamicPermission() default "";

}
