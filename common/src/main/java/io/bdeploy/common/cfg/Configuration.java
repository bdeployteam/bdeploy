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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import com.google.common.base.Splitter;

import io.bdeploy.common.cli.ToolBase;
import io.bdeploy.common.cli.data.DataTable;

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
    private final List<String> remaining = new ArrayList<>();

    /**
     * Add a set of command line arguments to the mapping. Arguments must currently
     * start with '--'.
     *
     * @param arguments the command line argument as passed to the program.
     */
    public void add(String... arguments) {
        boolean inRemaining = false;
        for (String arg : arguments) {
            if (inRemaining) {
                remaining.add(arg);
            } else if (arg.equals("--")) {
                inRemaining = true;
            } else if (arg.startsWith("--")) {
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
    public <T extends Annotation> T get(Class<? extends Annotation> target) {
        @SuppressWarnings("unchecked")
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
        RemainingArguments isRemaining = method.getAnnotation(RemainingArguments.class);
        ConfigurationNameMapping mapping = method.getAnnotation(ConfigurationNameMapping.class);
        if (mapping != null) {
            key = mapping.value();
        }

        if (isRemaining != null) {
            // instead of mapping, we simply inject all strings from "remaining"
            if (!method.getReturnType().isAssignableFrom(String[].class)) {
                throw new IllegalStateException("Receiver for remaining arguments must be of type String[]");
            }

            return remaining.toArray(new String[remaining.size()]);
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
        Class<? extends ConfigValidator<?>>[] cvs = null;
        if (validator != null) {
            cvs = validator.value();
        }

        UnaryOperator<Object> mapper = getMapper(method);

        Class<?> returnType = method.getReturnType();
        if (object != null && returnType.isAssignableFrom(object.getClass())) {
            validateOrThrow(object, method, cvs);
            Object result = object;
            if (returnType.isAssignableFrom(String.class)) {
                result = mapper.apply(object);
            }
            conversions.put(method, result);
            return result;
        }

        if (returnType.isPrimitive() && !(object instanceof String)) {
            // implicit conversion through boxing/unboxing. let's just hope the types match
            // ;)
            validateOrThrow(object, method, cvs);
            conversions.put(method, object);
            return object;
        }

        if (object instanceof List && returnType.isArray()) {
            List<?> list = (List<?>) object;
            // perform conversion for each of the elements.
            Object targetArray = Array.newInstance(returnType.getComponentType(), list.size());
            for (int i = 0; i < list.size(); ++i) {
                Array.set(targetArray, i, convertType(returnType.getComponentType(), (String) list.get(i), mapper));
            }
            validateOrThrow(targetArray, method, cvs);
            conversions.put(method, targetArray);
            return targetArray;
        }

        // check source type
        if (!(object instanceof String) && !returnType.isAnnotation()) {
            throw new IllegalStateException(getParameterConfigurationHint(returnType, method.getName(), object));
        }

        // do actual conversion
        conversion = convertType(returnType, (String) object, mapper);

        validateOrThrow(conversion, method, cvs);

        // remember the result in the mapping, so we don't need to convert back and
        // forth all the time.
        conversions.put(method, conversion);

        return conversion;
    }

    private String getParameterConfigurationHint(Class<?> returnType, String methodName, Object object) {
        StringBuilder hint = new StringBuilder();

        hint.append("Could not resolve " + methodName + " parameter. ");

        if (returnType.isArray()) {
            hint.append("Please specify parameter like this: " + methodName + "=<value1> " + methodName
                    + "=<value2>... or like this: " + methodName + "=<value1>,<value2>... ");
        } else {
            hint.append("Please specify parameter like this: " + methodName + "=<value>. ");
        }

        hint.append("Illegal conversion from non-string object to different type: "
                + (object == null ? "null" : object.getClass()) + " to " + returnType);

        return hint.toString();
    }

    private UnaryOperator<Object> getMapper(Method method) {
        ConfigurationValueMapping mapping = method.getAnnotation(ConfigurationValueMapping.class);
        UnaryOperator<Object> mapper = s -> s;

        if (mapping != null) {
            if (mapping.value() == ValueMapping.TO_LOWERCASE) {
                mapper = s -> s.toString().toLowerCase();
            } else if (mapping.value() == ValueMapping.TO_UPPERCASE) {
                mapper = s -> s.toString().toUpperCase();
            }
        }
        return mapper;
    }

    private void validateOrThrow(Object value, Method m, Class<? extends ConfigValidator<?>>[] validators) {
        if (validators == null) {
            return;
        }
        for (Class<? extends ConfigValidator<?>> validator : validators) {
            try {
                ValidationMessage msg = validator.getAnnotation(ValidationMessage.class);

                if (msg == null) {
                    throw new IllegalStateException("No validation message set on validator class: " + validator);
                }

                ConfigValidator<?> v = validator.getDeclaredConstructor().newInstance();
                if (!v.validate(cast(value))) {
                    throw new IllegalArgumentException("--" + m.getName() + ": " + String.format(msg.value(), value));
                }
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                throw new IllegalStateException("Cannot validate value: " + value + " using validator: " + validator, e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T cast(Object object) {
        return (T) object;
    }

    @SuppressWarnings("unchecked")
    private Object convertType(Class<?> target, String source, UnaryOperator<Object> mapper) {
        if (target.isAnnotation()) {
            return get((Class<? extends Annotation>) target);
        }
        if (source == null) {
            throw new IllegalArgumentException("Conversion of null source not possible");
        }
        if (target.equals(String.class)) {
            return mapper.apply(source);
        }
        if (target.equals(long.class)) {
            return Long.parseLong(source);
        }
        if (target.equals(int.class)) {
            return Integer.parseInt(source);
        }
        if (target.equals(short.class)) {
            return Short.parseShort(source);
        }
        if (target.equals(byte.class)) {
            return Byte.parseByte(source);
        }
        if (target.equals(boolean.class)) {
            return Boolean.parseBoolean(source);
        }
        if (target.equals(double.class)) {
            return Double.parseDouble(source);
        }
        if (target.equals(float.class)) {
            return Float.parseFloat(source);
        }
        if (target.equals(char.class)) {
            if (source.length() > 1) {
                throw new IllegalArgumentException("Character conversion with input length > 1: " + source);
            }
            return source.charAt(0);
        }
        if (target.isEnum()) {
            try {
                return target.getMethod("valueOf", String.class).invoke(null, mapper.apply(source));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new IllegalStateException(
                        "internal error resolving enumeration literal for " + target + " '" + source + "'", e);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException(e.getTargetException());
            }
        }
        if (target.isArray()) {
            List<String> split = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(source);
            Object targetArray = Array.newInstance(target.getComponentType(), split.size());
            for (int i = 0; i < split.size(); ++i) {
                Array.set(targetArray, i, convertType(target.getComponentType(), split.get(i), mapper));
            }
            return targetArray;
        }
        throw new IllegalStateException("Unsupported conversion to " + target);
    }

    /**
     * Analyze {@link Help} annotations on the target {@link Annotation} and print
     * out help information on the given {@link PrintStream}.
     */
    public static void formatHelp(Class<? extends Annotation> cfg, DataTable target) {
        List<Method> declaredMethods = Arrays.asList(cfg.getDeclaredMethods()).stream()
                .sorted((a, b) -> a.getName().compareTo(b.getName())).collect(Collectors.toList());
        for (Method m : declaredMethods) {
            if (m.getAnnotation(RemainingArguments.class) != null) {
                continue; // skip in help.
            }

            Help h = m.getAnnotation(Help.class);
            ConfigurationNameMapping mapping = m.getAnnotation(ConfigurationNameMapping.class);
            String name = m.getName();
            if (mapping != null) {
                name = mapping.value();
            }

            EnvironmentFallback env = m.getAnnotation(EnvironmentFallback.class);
            String envFb = "";
            if (env != null) {
                envFb = String.format(" (Environment variable '%1$s' is used as fallback if not given).", env.value());
            }

            String defVal = "";
            if (m.getDefaultValue() != null && h.arg()) {
                if (m.getDefaultValue().getClass().isArray()) {
                    defVal = Arrays.asList((Object[]) m.getDefaultValue()).toString();
                } else {
                    defVal = m.getDefaultValue().toString();
                }
            }

            if (h != null) {
                target.row().cell(" --" + name + (h.arg() ? "=ARG" : "")).cell(h.value() + envFb).cell(defVal).build();
            } else {
                target.row().cell(" --" + name).cell(env != null ? envFb.substring(1) : "").cell(defVal).build();
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

    /**
     * Marker annotation which denotes an element to receive all remaining arguments. The type of the receiver must be String[]
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface RemainingArguments {

    }

    public enum ValueMapping {
        TO_UPPERCASE,
        TO_LOWERCASE
    }

    /**
     * Annotated field's value will be mapped on injection using the given {@link ValueMapping} policy.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface ConfigurationValueMapping {

        ValueMapping value();
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

        Class<? extends ConfigValidator<?>>[] value();
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
