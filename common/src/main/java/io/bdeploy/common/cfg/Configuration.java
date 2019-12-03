/*******************************************************************************
 *  Copyright (c) 2017 SSI Schaefer IT Solutions GmbH and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      SSI Schaefer IT Solutions GmbH
 *******************************************************************************/
package io.bdeploy.common.cfg;

import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import com.google.common.base.Splitter;

import io.bdeploy.common.cli.ToolBase;

/**
 * The {@link Configuration} is basically a wrapper around a {@link Map} which
 * exposes access to the {@link Map} through {@link Annotation}s.
 * <p>
 * Any arbitrary {@link Annotation} can be defined, including default values,
 * and mapped to the {@link Configuration} using {@link #get(Class)}.
 * <p>
 * The mapped {@link Annotation} will access the underlying {@link Map} on every
 * method call. If a key exists in the {@link Map} that corresponds to the name
 * of the {@link Annotation}'s {@link Method}, it will be converted to the
 * target type and returned. Otherwise the default value of the {@link Method}
 * is returned.
 * <p>
 * There is (limited) type conversion capabilities. Mainly this functionality
 * exists to be able to map {@link String}s (e.g. when mapping a command line
 * using {@link #add(String...)}) to the target types of the {@link Annotation}
 * {@link Method}s.
 */
public class Configuration {

    private final Map<String, Object> objects = new TreeMap<>();
    private final Map<Method, Object> conversions = new HashMap<>();

    /**
     * Add a set of command line arguments to the mapping. Arguments must currently
     * start with '--'.
     *
     * @param arguments the command line argument as passed to the program.
     */
    public void add(String... arguments) {
        for (String arg : arguments) {
            if (arg.startsWith("--")) {
                String stripped = arg.substring(2);
                int equalsIndex = stripped.indexOf('=');
                if (equalsIndex != -1) {
                    addKeyValueArgument(stripped, equalsIndex);
                } else {
                    objects.put(stripped, Boolean.TRUE);
                }
            } else {
                // unsupported right now
                throw new IllegalStateException("Unsupported argument format: " + arg);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void addKeyValueArgument(String stripped, int equalsIndex) {
        String key = stripped.substring(0, equalsIndex);
        String value = stripped.substring(equalsIndex + 1);

        if (objects.containsKey(key)) {
            Object existing = objects.get(key);
            if (existing instanceof List) {
                ((List<Object>) existing).add(value);
            } else {
                List<Object> l = new ArrayList<>();
                l.add(existing);
                l.add(value);
                objects.put(key, l);
            }
        } else {
            objects.put(key, value);
        }
    }

    public Map<String, Object> getAllRawObjects() {
        return objects;
    }

    /**
     * Adds arbitrary ({@link String}) properties to the mapping.
     * <p>
     * Typically used to add a configuration file or system properties to the
     * mapping
     *
     * @param properties the entries to add to the mapping.
     */
    public void add(Properties properties) {
        properties.forEach((k, v) -> objects.put((String) k, v));
    }

    /**
     * Returns an instance of the given {@link Annotation}, mapping each
     * {@link Method} to a value in the mapping where the {@link Method} name is the
     * key into the wrapped {@link Map}.
     *
     * @param target the {@link Class} to map this {@link Configuration} to.
     * @return a proxy mapping to the {@link Configuration}.
     */
    @SuppressWarnings("unchecked")
    public <T extends Annotation> T get(Class<? extends Annotation> target) {
        T proxy = (T) Proxy.newProxyInstance(target.getClassLoader(), new Class<?>[] { target }, this::doMap);

        // access each method once to initialize cache, run conversions and especially validations.
        List<Exception> validationProblems = new ArrayList<>();
        for (Method m : target.getDeclaredMethods()) {
            try {
                doMap(proxy, m, null);
            } catch (Exception e) {
                validationProblems.add(e);
            }
        }

        if (!validationProblems.isEmpty()) {
            ConfigValidationException collector = new ConfigValidationException();
            for (Exception problem : validationProblems) {
                collector.addSuppressed(problem);
            }
            throw collector;
        }

        return proxy;
    }

    private Object doMap(Object proxy, Method method, Object[] arguments) {
        String key = method.getName();
        ConfigurationNameMapping mapping = method.getAnnotation(ConfigurationNameMapping.class);
        if (mapping != null) {
            key = mapping.value();
        }

        Object value = objects.get(key);
        if (value == null && !ToolBase.isTestMode()) {
            EnvironmentFallback fallback = method.getAnnotation(EnvironmentFallback.class);
            if (fallback != null) {
                value = System.getenv(fallback.value());
            }
        }

        if (value == null && !method.getReturnType().isAnnotation()) {
            return method.getDefaultValue();
        }

        return doConvert(method, value);
    }

    private Object doConvert(Method method, Object object) {
        // lookup existing conversion
        Object conversion = conversions.get(method);
        if (conversion != null) {
            return conversion;
        }

        Validator validator = method.getAnnotation(Validator.class);
        ValidationMessage vmsg = null;
        Class<? extends ConfigValidator<?>> cv = null;
        if (validator != null) {
            cv = validator.value();
            vmsg = cv.getAnnotation(ValidationMessage.class);

            if (vmsg == null) {
                throw new IllegalStateException("No validation message set on validator class: " + cv);
            }
        }

        Class<?> returnType = method.getReturnType();
        if (object != null && returnType.isAssignableFrom(object.getClass())) {
            validateOrThrow(object, method, cv, vmsg);
            return object;
        }

        if (returnType.isPrimitive() && !(object instanceof String)) {
            // implicit conversion through boxing/unboxing. let's just hope the types match
            // ;)
            validateOrThrow(object, method, cv, vmsg);
            return object;
        }

        if (object instanceof List && returnType.isArray()) {
            List<?> list = (List<?>) object;
            // perform conversion for each of the elements.
            Object targetArray = Array.newInstance(returnType.getComponentType(), list.size());
            for (int i = 0; i < list.size(); ++i) {
                Array.set(targetArray, i, convertType(returnType.getComponentType(), (String) list.get(i)));
            }
            validateOrThrow(targetArray, method, cv, vmsg);
            conversions.put(method, targetArray);
            return targetArray;
        }

        // check source type
        if (!(object instanceof String) && !returnType.isAnnotation()) {
            throw new IllegalStateException("Illegal conversion from non-string object to different type: "
                    + (object == null ? "null" : object.getClass()) + " to " + returnType);
        }

        // do actual conversion
        conversion = convertType(returnType, (String) object);

        validateOrThrow(conversion, method, cv, vmsg);

        // remember the result in the mapping, so we don't need to convert back and
        // forth all the time.
        conversions.put(method, conversion);

        return conversion;
    }

    private void validateOrThrow(Object value, Method m, Class<? extends ConfigValidator<?>> validator, ValidationMessage msg) {
        if (validator == null) {
            return;
        }

        try {
            ConfigValidator<?> v = validator.getDeclaredConstructor().newInstance();
            if (!v.validate(cast(value))) {
                throw new IllegalArgumentException("--" + m.getName() + ": " + String.format(msg.value(), value));
            }
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new IllegalStateException("Cannot validate value: " + value + " using validator: " + validator, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T cast(Object object) {
        return (T) object;
    }

    @SuppressWarnings("unchecked")
    private Object convertType(Class<?> target, String source) {
        if (target.equals(String.class)) {
            return source;
        } else if (target.equals(long.class)) {
            return Long.parseLong(source);
        } else if (target.equals(int.class)) {
            return Integer.parseInt(source);
        } else if (target.equals(short.class)) {
            return Short.parseShort(source);
        } else if (target.equals(byte.class)) {
            return Byte.parseByte(source);
        } else if (target.equals(boolean.class)) {
            return Boolean.parseBoolean(source);
        } else if (target.equals(double.class)) {
            return Double.parseDouble(source);
        } else if (target.equals(float.class)) {
            return Float.parseFloat(source);
        } else if (target.equals(char.class)) {
            if (source == null) {
                throw new IllegalArgumentException("Character conversion of null source not possible");
            }
            if (source.length() > 1) {
                throw new IllegalArgumentException("Character conversion with input length > 1: " + source);
            }
            return source.charAt(0);
        } else if (target.isEnum()) {
            try {
                return target.getMethod("valueOf", String.class).invoke(null, source);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new IllegalStateException(
                        "internal error resolving enumeration literal for " + target + " '" + source + "'", e);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException(e.getTargetException());
            }
        } else if (target.isArray()) {
            List<String> split = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(source);
            Object targetArray = Array.newInstance(target.getComponentType(), split.size());
            for (int i = 0; i < split.size(); ++i) {
                Array.set(targetArray, i, convertType(target.getComponentType(), split.get(i)));
            }
            return targetArray;
        } else if (target.isAnnotation()) {
            return get((Class<? extends Annotation>) target);
        }

        throw new IllegalStateException("Unsupported conversion to " + target);
    }

    /**
     * Analyze {@link Help} annotations on the target {@link Annotation} and print
     * out help information on the given {@link PrintStream}.
     */
    public static void formatHelp(Class<? extends Annotation> cfg, PrintStream target, String indent) {
        for (Method m : cfg.getDeclaredMethods()) {
            Help h = m.getAnnotation(Help.class);
            if (h != null) {
                target.println(indent + String.format("%1$20s%2$4s: %3$s", "--" + m.getName(), h.arg() ? "=ARG" : "", h.value()));
            } else {
                target.println(indent + m.getName());
            }

            EnvironmentFallback env = m.getAnnotation(EnvironmentFallback.class);
            if (env != null) {
                target.println(indent + String.format("%1$24s  (Environment variable '%2$s' is used as fallback if not given)",
                        "", env.value()));
            }
        }
    }

    /**
     * Maps the annotated method to another property name in the context.
     * <p>
     * This can be used to map an arbitrary {@link Annotation} {@link Method} to
     * another name, e.g. to access system properties with property names that are
     * not valid method names in Java.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface ConfigurationNameMapping {

        String value();
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(value = { ElementType.METHOD, ElementType.TYPE })
    public @interface Help {

        /**
         * @return the help text for the annotated element.
         */
        String value();

        /**
         * @return whether the option accepts an argument (will be rendered accordingly
         *         in help text).
         */
        boolean arg() default true;
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(value = { ElementType.METHOD })
    public @interface Validator {

        Class<? extends ConfigValidator<?>> value();
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(value = ElementType.TYPE)
    public @interface ValidationMessage {

        String value() default "Validation failed for $1%s";
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(value = { ElementType.METHOD })
    public @interface EnvironmentFallback {

        /**
         * @return the name of the environment variable to query in case the parameter is not explicitly set.
         */
        String value();
    }

    @FunctionalInterface
    public interface ConfigValidator<T> {

        /**
         * Validates a single value.
         *
         * @param value the parameter value.
         * @return whether the value is valid.
         */
        public boolean validate(T value);
    }
}
