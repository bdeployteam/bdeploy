package io.bdeploy.bhive.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHive.Operation;

/**
 * Extracts field values from Hive {@link Operation}s for auditing purposes.
 */
public class AuditParameterExtractor {

    private static final Logger log = LoggerFactory.getLogger(AuditParameterExtractor.class);

    /**
     * Prevents auditing of the given Field.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface NoAudit {
    }

    /**
     * Determines how a field should be audited
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface AuditWith {

        AuditParameterExtractor.AuditStrategy value() default AuditStrategy.TO_STRING;
    }

    /**
     * Defines the strategy used to extract a fields value. This directly defines the way a value is represented in the audit
     * logs.
     */
    public enum AuditStrategy {
        TO_STRING(Object::toString),
        COLLECTION_SIZE(x -> Integer.toString(((Collection<?>) x).size())),
        COLLECTION_PEEK(x -> {
            List<String> items = new ArrayList<>();
            if (x instanceof Collection) {
                Collection<?> coll = ((Collection<?>) x);
                coll.stream().limit(3).map(Object::toString).forEach(items::add);
                if (coll.size() > 3) {
                    items.add("...");
                }
            } else if (x instanceof Map) {
                Map<?, ?> m = ((Map<?, ?>) x);
                m.entrySet().stream().limit(3).map(Object::toString).forEach(items::add);
                if (m.size() > 3) {
                    items.add("...");
                }
            }
            return items.toString();
        });

        private final Function<Object, String> converter;

        private AuditStrategy(Function<Object, String> converter) {
            this.converter = converter;
        }
    }

    /**
     * @param op The {@link Operation} to extract parameters from. Audit parameters are extracted from the {@link Operation}s
     *            fields
     * @return a mapping from field name to extracted value based on the chosen strategy.
     * @see AuditStrategy
     * @see AuditWith
     * @see NoAudit
     */
    public Map<String, String> extract(BHive.Operation<?> op) {
        Class<?> clazz = op.getClass();
        Map<String, String> result = new TreeMap<>();

        for (Field field : clazz.getDeclaredFields()) {
            AuditParameterExtractor.NoAudit na = field.getAnnotation(AuditParameterExtractor.NoAudit.class);
            if (na != null) {
                if (log.isTraceEnabled()) {
                    log.trace("Skipping audit of {}", field);
                }
                continue;
            }

            Function<Object, String> converter = null;
            AuditParameterExtractor.AuditStrategy strategy = AuditStrategy.TO_STRING;
            AuditParameterExtractor.AuditWith with = field.getAnnotation(AuditParameterExtractor.AuditWith.class);
            if (with != null) {
                strategy = with.value();
            }

            if (converter == null) {
                converter = strategy.converter;
            }

            field.setAccessible(true);
            try {
                Object fieldValue = field.get(op);
                if (fieldValue != null) {
                    result.put(field.getName(), converter.apply(fieldValue));
                }
            } catch (IllegalArgumentException | IllegalAccessException e) {
                log.debug("Cannot read value of {}", field, e);
            }
        }

        return result;
    }

}