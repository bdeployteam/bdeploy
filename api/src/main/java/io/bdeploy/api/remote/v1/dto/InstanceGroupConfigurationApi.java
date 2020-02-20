package io.bdeploy.api.remote.v1.dto;

public class InstanceGroupConfigurationApi {

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

}
