package io.bdeploy.interfaces.descriptor.template;

public class InstanceTemplateVariable {

    /**
     * A unique ID in the template
     */
    public String uid;

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

}
