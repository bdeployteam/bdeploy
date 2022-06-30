package io.bdeploy.interfaces.configuration.system;

import java.util.Map;
import java.util.TreeMap;

/**
 * Represents a System, which is an encapsulation of instances within an instance group, e.g. "Productive System", "Test System",
 * etc.
 */
public class SystemConfiguration {

    public static final String FILE_NAME = "system.json";

    /**
     * The unique ID of the system.
     */
    public String uuid;

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
    public Map<String, String> configVariables = new TreeMap<>();

}
