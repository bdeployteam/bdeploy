package io.bdeploy.interfaces.descriptor.template;

import com.fasterxml.jackson.annotation.JsonAlias;

public class TemplateParameter {

    /**
     * The UID of the parameter as defined in the applications app-info.yaml.
     */
    @JsonAlias("uid")
    public String id;

    /**
     * The value that should be assigned to the parameter.
     * <p>
     * You can leave out the value to add an optional parameter with its default value to the configuration.
     */
    public String value;

}
