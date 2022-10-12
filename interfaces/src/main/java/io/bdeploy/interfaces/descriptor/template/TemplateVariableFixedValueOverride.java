package io.bdeploy.interfaces.descriptor.template;

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

}
