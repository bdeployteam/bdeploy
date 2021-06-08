package io.bdeploy.interfaces.configuration.dcu;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates configuration for a single command (start/stop) for an
 * {@link ApplicationConfiguration}.
 */
public class CommandConfiguration {

    /**
     * The executable. Either a relative path, or a path with a manifest reference
     * (e.g. {{M:jdk}}/bin/java).
     */
    public String executable;

    /**
     * The pre-rendered parameters.
     */
    public List<ParameterConfiguration> parameters = new ArrayList<>();

}
