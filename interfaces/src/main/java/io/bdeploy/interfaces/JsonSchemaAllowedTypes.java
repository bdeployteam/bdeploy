package io.bdeploy.interfaces;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Specifies additional types which are allowed in the JSON Schema.
 * <p>
 * Jackson will happily read booleans, numbers etc. into strings.
 */
@Retention(RUNTIME)
@Target(ElementType.FIELD)
public @interface JsonSchemaAllowedTypes {

    Class<?>[] value();
}
