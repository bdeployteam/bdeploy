package io.bdeploy.interfaces.configuration.instance;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.variables.ApplicationParameterValueResolver;
import io.bdeploy.interfaces.variables.ApplicationVariableResolver;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.ManifestSelfResolver;

/**
 * A {@link InstanceNodeConfiguration} marries each {@link ApplicationDescriptor}
 * with actual values for configurable parts (e.g. parameters).
 */
public class InstanceNodeConfiguration {

    public static final String FILE_NAME = "node.json";

    /**
     * The human readable name of the instance.
     * <p>
     * Redundant copy of the information from {@link InstanceConfiguration} for easier handling on the minion(s).
     */
    public String name;

    /**
     * A unique ID identifying this deployment, even if the name changes on the
     * server. Used to calculate the location where things are deployed.
     * <p>
     * Redundant copy of the information from {@link InstanceConfiguration} for easier handling on the minion(s).
     */
    public String uuid;

    /**
     * Whether this instance should be automatically stated on startup of the minion.
     * <p>
     * Redundant copy of the information from {@link InstanceConfiguration} for easier handling on the minion(s).
     */
    public boolean autoStart = false;

    /**
     * The intended use of the deployed software.
     * <p>
     * Redundant copy of the information from {@link InstanceConfiguration} for easier handling on the minion(s).
     */
    public InstancePurpose purpose;

    /**
     * The key of the product which was used to create the instance.
     * <p>
     * Redundant copy of the information from {@link InstanceConfiguration} for easier handling on the minion(s).
     */
    public Manifest.Key product;

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
        ProcessGroupConfiguration pgc = new ProcessGroupConfiguration();
        pgc.name = name;
        pgc.uuid = uuid;
        pgc.autoStart = autoStart;

        // each downstream application has a scoped provider allowing to directly
        // reference parameter values of parameters in the same scope.
        for (ApplicationConfiguration app : applications) {
            CompositeResolver list = new CompositeResolver();
            list.add(new ApplicationVariableResolver(app));
            list.add(new ApplicationParameterValueResolver(app.name, valueResolver));
            list.add(new ManifestSelfResolver(app.application, valueResolver));
            list.add(valueResolver);
            ProcessConfiguration pc = app.renderDescriptor(list);
            pgc.applications.add(pc);
        }

        return pgc;
    }

    /**
     * Applies redundant copies of informative fields from the "parent" {@link InstanceConfiguration}
     */
    public void copyRedundantFields(InstanceConfiguration cfg) {
        uuid = cfg.uuid;
        name = cfg.name;
        autoStart = cfg.autoStart;
        purpose = cfg.purpose;
        product = cfg.product;
    }
}
