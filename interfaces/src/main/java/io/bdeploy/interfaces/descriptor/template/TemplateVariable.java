package io.bdeploy.interfaces.descriptor.template;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TemplateVariable {

    /**
     * A unique ID in the template
     */
    @JsonAlias("uid")
    public String id;

    // Compat with 4.x
    @Deprecated(forRemoval = true)
    @JsonProperty("uid")
    public String getUid() {
        return id;
    };

    /**
     * A short human readable name of the variable.
     */
    public String name;

    /**
     * The description which is shown to the user when querying the parameter.
     */
    public String description;

    /**
     * Default value as string, can be interpreted as number, etc. depending on the target parameter type.
     */
    public String defaultValue;

    /**
     * A list of values suggested by the variable input field in the UI.
     */
    public List<String> suggestedValues;

}
