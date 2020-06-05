package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.interfaces.configuration.pcu.ProcessControlConfiguration;

public class InstanceTemplateApplication {

    /**
     * The short ID of the application, as given in the product-info.yaml file of the product.
     */
    public String application;

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
     * Note that the default is 'MANUAL' start type, even if the application supports others.
     */
    public ProcessControlConfiguration processControl = ProcessControlConfiguration.createDefault();

    public List<InstanceTemplateParameter> startParameters = new ArrayList<>();

}
