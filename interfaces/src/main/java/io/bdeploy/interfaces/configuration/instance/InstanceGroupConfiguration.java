package io.bdeploy.interfaces.configuration.instance;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.interfaces.settings.CustomPropertyDescriptor;

public class InstanceGroupConfiguration {

    /**
     * The name under which the {@link InstanceGroupConfiguration} can be found in customer manifest.
     */
    public static final String FILE_NAME = "instance-group.json";

    /**
     * The name of the instance group. Used as ID and path in the filesystem to the groups BHive.
     */
    public String name;

    /**
     * A human readable title for the instance group
     */
    public String title;

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

    /**
     * Whether this instance group has been attached to a central server
     */
    public boolean managed;

    /**
     * Property definitions for instances of this instance group
     */
    public List<CustomPropertyDescriptor> instanceProperties = new ArrayList<>();
}
