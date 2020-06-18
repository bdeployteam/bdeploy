package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.bdeploy.interfaces.configuration.pcu.ProcessControlConfiguration;

public class TemplateApplication {

    /**
     * The short ID of the application, as given in the product-info.yaml file of the product.
     */
    public String application;

    /**
     * ID of another application template, which should be the basis of this template.
     */
    public String template;

    /**
     * The name of the application configuration.
     */
    public String name;

    /**
     * A description of the application created.
     */
    public String description;

    /**
     * The process control configuration to apply.
     * <p>
     * This is interpreted as {@link ProcessControlConfiguration}, but defined as Map to allow partial deserialization.
     */
    public Map<String, Object> processControl = new TreeMap<>();

    /**
     * A set of parameters to configure on the process resulting from this template
     */
    public List<TemplateParameter> startParameters = new ArrayList<>();

    /**
     * Avoid multiple resolutions on multiple references.
     */
    public transient boolean resolved;

}
