package io.bdeploy.interfaces.descriptor.template;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.interfaces.JsonSchemaAllowedTypes;

public class TemplateVariableFixedValueOverride {

    /**
     * The id of the variable to set a fixed value for recursively from a template.
     */
    public String id;

    /**
     * The value to set. Follows the same rules as the {@link TemplateVariable#defaultValue}.
     */
    @JsonSchemaAllowedTypes({ String.class, Number.class, Boolean.class })
    public String value;

    @JsonCreator
    public TemplateVariableFixedValueOverride(@JsonProperty("id") String id, @JsonProperty("value") String value) {
        this.id = id;
        this.value = value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        TemplateVariableFixedValueOverride other = (TemplateVariableFixedValueOverride) obj;
        return Objects.equals(id, other.id) && Objects.equals(value, other.value);
    }
}
