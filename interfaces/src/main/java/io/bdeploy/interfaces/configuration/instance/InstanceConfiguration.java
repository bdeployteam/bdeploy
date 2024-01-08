package io.bdeploy.interfaces.configuration.instance;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.interfaces.configuration.VariableConfiguration;

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
    @JsonAlias("uuid")
    public String id;

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
     * The associated system for this instance.
     */
    public Manifest.Key system;

    /**
     * The key of the tree which contains the current configuration file state.
     */
    public ObjectId configTree;

    /**
     * Schedule background uninstallation of old instance versions
     */
    public boolean autoUninstall;

    /**
     * Regular expression to filter products before calculating newest available version
     */
    public String productFilterRegex;

    /**
     * Collection of instance variables which can be used to provide values to parameters.
     */
    public List<VariableConfiguration> instanceVariables = new ArrayList<>();

}
