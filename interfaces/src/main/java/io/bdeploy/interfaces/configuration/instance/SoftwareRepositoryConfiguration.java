package io.bdeploy.interfaces.configuration.instance;

public class SoftwareRepositoryConfiguration {

    /**
     * The name under which the {@link SoftwareRepositoryConfiguration} can be found in customer manifest.
     */
    public static final String FILE_NAME = "repository.json";

    /**
     * The human readable name of the instance group
     */
    public String name;

    /**
     * Additional descriptive text.
     */
    public String description;

}
