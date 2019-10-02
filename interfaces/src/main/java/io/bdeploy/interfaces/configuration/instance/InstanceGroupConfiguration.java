package io.bdeploy.interfaces.configuration.instance;

import io.bdeploy.bhive.model.ObjectId;

public class InstanceGroupConfiguration {

    /**
     * The name under which the {@link InstanceGroupConfiguration} can be found in customer manifest.
     */
    public static final String FILE_NAME = "instance-group.json";

    /**
     * The human readable name of the instance group
     */
    public String name;

    /**
     * Additional descriptive text.
     */
    public String description;

    /**
     * Logo object in the hive if present
     */
    public ObjectId logo;

    /**
     * Schedule background deletion of old and unused product versions
     */
    public boolean autoDelete;
}
