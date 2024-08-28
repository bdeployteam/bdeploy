package io.bdeploy.interfaces.configuration.dcu;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonValueInstantiator;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration.LVCInstatiator;

/**
 * A potentially linked value; While {@link #value} needs to fulfill all criteria for the associated data type (e.g. number), the
 * {@link #linkExpression} can contain an arbitrary format string using expansion expressions. This must result in a valid value
 * according to the associated data type only *after* evaluation on the target node.
 * <p>
 * If {@link #linkExpression} is set, it will take precedence over the value.
 */
@JsonValueInstantiator(value = LVCInstatiator.class)
public class LinkedValueConfiguration {

    /**
     * A plain value, must be valid for the associated data type (e.g. number, port, etc.).
     */
    @JsonPropertyDescription("A plain value. May not contain expansions. Ignored if linkExpression is given.")
    public String value;

    /**
     * A link expression containing one or more variable expansions.
     */
    @JsonPropertyDescription("A link expression containing one or more variable expansions (e.g. {{X:var}}).")
    public String linkExpression;

    /** Convenience and migration constructor which decides whether the value is a plain value or an expression */
    public LinkedValueConfiguration(String value) {
        if (value != null && value.contains("{{")) {
            this.linkExpression = value;
        } else {
            this.value = value;
        }
    }

    /**
     * @return the {@link String} value suitable for pre-rendering and pre-processing, either a plain value or an expression.
     */
    public String getPreRenderable() {
        return linkExpression != null ? linkExpression : value;
    }

    /**
     * {@link ValueInstantiator} which can create the value from a plain {@link String} for compatibility with previous data
     * format.
     */
    public static final class LVCInstatiator extends ValueInstantiator {

        @Override
        public boolean canCreateFromString() {
            return true; // e.g. all parameters
        }

        @Override
        public Object createFromString(DeserializationContext ctxt, String value) throws IOException {
            return new LinkedValueConfiguration(value);
        }

        @Override
        public boolean canCreateFromBoolean() {
            return true; // e.g. endpoint 'secure'
        }

        @Override
        public Object createFromBoolean(DeserializationContext ctxt, boolean value) throws IOException {
            return new LinkedValueConfiguration(Boolean.toString(value));
        }

        @Override
        public boolean canCreateFromInt() {
            return true; // e.g. parameter values/defaultValues
        }

        @Override
        public Object createFromInt(DeserializationContext ctxt, int value) throws IOException {
            return new LinkedValueConfiguration(Integer.toString(value));
        }

        @Override
        public boolean canCreateFromLong() {
            return true; // e.g. parameter values/defaultValues
        }

        @Override
        public Object createFromLong(DeserializationContext ctxt, long value) throws IOException {
            return new LinkedValueConfiguration(Long.toString(value));
        }

        @Override
        public boolean canCreateUsingDefault() {
            return true;
        }

        @Override
        public Object createUsingDefault(DeserializationContext ctxt) throws IOException {
            return new LinkedValueConfiguration(null);
        }

    }

    // TODO ManagedServersResourceImpl#getUpdates ensures that we do not communicate with managed servers older than version 5.
    // This deprecated code is therefore ready for actual removal.

    /**
     * Compatibility module when we act as REST client so we send the original format of values.
     * <p>
     * This enables new CENTRAL minions to still save configuration with older MANAGED minions (4.5.1 and older).
     * <p>
     * New minions will promptly use {@link LVCInstatiator} to convert back. In the future we can remove this
     * bridge and everybody will use the new format.
     *
     * @deprecated this is here for compatibility of central with managed servers which run older releases. This should be removed
     *             at the point where central servers are allowed to force managed servers to be newer than 4.6.0.
     */
    @Deprecated(since = "4.6.0", forRemoval = true)
    public static final class LVCModule extends SimpleModule {

        private static final long serialVersionUID = 1L;
        public static final LVCModule LVC_MODULE = new LVCModule();

        private LVCModule() {
            addSerializer(LinkedValueConfiguration.class, new StdSerializer<>(LinkedValueConfiguration.class) {

                private static final long serialVersionUID = 1L;

                @Override
                public void serialize(LinkedValueConfiguration value, JsonGenerator gen, SerializerProvider provider)
                        throws IOException {
                    gen.writeString(value.getPreRenderable());
                }
            });
        }
    }

}
