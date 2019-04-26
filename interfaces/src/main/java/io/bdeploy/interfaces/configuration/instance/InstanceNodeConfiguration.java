package io.bdeploy.interfaces.configuration.instance;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.variables.VariableResolver;

/**
 * A {@link InstanceNodeConfiguration} marries each {@link ApplicationDescriptor}
 * with actual values for configurable parts (e.g. parameters).
 */
public class InstanceNodeConfiguration {

    public static final String FILE_NAME = "node.json";

    /**
     * The human readable name of the instance.
     * <p>
     * Redundant copy of the information from InstanceConfiguration for easier handling on the minion(s).
     */
    public String name;

    /**
     * A unique ID identifying this deployment, even if the name changes on the
     * server. Used to calculate the location where things are deployed.
     */
    public String uuid;

    /**
     * Whether this instance should be automatically stated on startup of the minion.
     */
    public boolean autoStart = false;

    /**
     * All application configurations.
     */
    public final List<ApplicationConfiguration> applications = new ArrayList<>();

    /**
     * Render this {@link InstanceNodeConfiguration} to a {@link ProcessGroupConfiguration}
     * consumable by the PCU.
     *
     * @param valueResolver a resolver queried for all variables to expand.
     * @return the rendered {@link ProcessGroupConfiguration}, which is machine specific.
     */
    public ProcessGroupConfiguration renderDescriptor(VariableResolver valueResolver) {
        ProcessGroupConfiguration dd = new ProcessGroupConfiguration();
        dd.name = name;
        dd.uuid = uuid;
        dd.autoStart = autoStart;

        // each downstream application has a scoped provider allowing to directly
        // reference parameter values of parameters in the same scope.
        applications.stream().map(a -> a.renderDescriptor(valueResolver.scopedTo(a.name, a.application)))
                .forEach(dd.applications::add);

        return dd;
    }
}
