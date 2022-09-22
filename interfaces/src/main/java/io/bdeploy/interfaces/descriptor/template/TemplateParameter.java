package io.bdeploy.interfaces.descriptor.template;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TemplateParameter {

    /**
     * The UID of the parameter as defined in the applications app-info.yaml.
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
     * The value that should be assigned to the parameter.
     * <p>
     * You can leave out the value to add an optional parameter with its default value to the configuration.
     */
    public String value;

}
