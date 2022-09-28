package io.bdeploy.interfaces.descriptor.application;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Encapsulates information about a platform specific executable.
 */
public class ExecutableDescriptor {

    /**
     * Relative path to the launcher executable (relative to application
     * installation directory).
     * <p>
     * Alternatively may reference another manifest, e.g. {{M:jdk}}/bin/java.
     */
    @JsonPropertyDescription("The relative path (from app-info.yaml) to the executable to be launched. Can also reference other manifests (e.g. runtime dependencies) using e.g. '{{M:manifest-name}}/executable.exe'")
    public String launcherPath;

    /**
     * Definition of parameters accepted/required by the launcher.
     * <p>
     * <b>IMPORTANT</b>: The order of the parameters is important. It defines
     * the order of parameters on the final command line.
     */
    @JsonPropertyDescription("The description of each possible parameter which can be configured through BDeploy on each concrete process using this application.")
    public List<ParameterDescriptor> parameters = new ArrayList<>();

}
