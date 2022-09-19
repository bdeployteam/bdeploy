package io.bdeploy.interfaces.configuration.system;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.bdeploy.interfaces.configuration.VariableConfiguration;

/**
 * Represents a System, which is an encapsulation of instances within an instance group, e.g. "Productive System", "Test System",
 * etc.
 */
public class SystemConfiguration {

    public static final String FILE_NAME = "system.json";

    /**
     * The unique ID of the system.
     */
    @JsonAlias("uuid")
    public String id;

    /**
     * The human readable name of the system.
     */
    public String name;

    /**
     * The human readable description of the system.
     */
    public String description;

    /**
     * The available globally addressable configuration variables
     */
    public List<VariableConfiguration> systemVariables = new ArrayList<>();

}
