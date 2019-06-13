package io.bdeploy.interfaces.descriptor.application;

import java.util.ArrayList;
import java.util.List;

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
    public String launcherPath;

    /**
     * Definition of parameters accepted/required by the launcher.
     * <p>
     * <b>IMPORTANT</b>: The order of the parameters is important. It defines
     * the order of parameters on the final command line.
     */
    public List<ParameterDescriptor> parameters = new ArrayList<>();

}
