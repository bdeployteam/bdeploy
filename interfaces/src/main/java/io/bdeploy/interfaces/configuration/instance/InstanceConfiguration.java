package io.bdeploy.interfaces.configuration.instance;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;

/**
 * Represents a single instance within an InstanceGroup.
 */
public class InstanceConfiguration {

    public static final String FILE_NAME = "instance.json";

    public enum InstancePurpose {
        @JsonEnumDefaultValue
        PRODUCTIVE,
        TEST,
        DEVELOPMENT
    }

    /**
     * Globally unique identifier of the instance.
     */
    public String uuid;

    /**
     * Short name of the instance.
     */
    public String name;

    /**
     * Optional Human readable description of the instance.
     */
    public String description;

    /**
     * Whether the instance should be started automatically when starting the minion(s).
     */
    public boolean autoStart = false;

    /**
     * The intended use of the deployed software.
     */
    public InstancePurpose purpose = InstancePurpose.DEVELOPMENT;

    /**
     * The key of the product which was used to create the instance.
     */
    public Manifest.Key product;

    /**
     * The key of the tree which contains the current configuration file state.
     */
    public ObjectId configTree;

    /**
     * Schedule background uninstallation of old instance versions
     */
    public boolean autoUninstall;

}
