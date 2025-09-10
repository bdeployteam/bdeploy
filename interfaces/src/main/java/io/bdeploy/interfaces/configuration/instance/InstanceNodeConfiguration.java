package io.bdeploy.interfaces.configuration.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

import javax.naming.spi.Resolver;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.configuration.system.SystemConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.endpoints.CommonEndpointHelper;
import io.bdeploy.interfaces.variables.ApplicationParameterProvider;
import io.bdeploy.interfaces.variables.ApplicationParameterValueResolver;
import io.bdeploy.interfaces.variables.ApplicationVariableResolver;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.ConditionalExpressionResolver;
import io.bdeploy.interfaces.variables.DelayedVariableResolver;
import io.bdeploy.interfaces.variables.EmptyVariableResolver;
import io.bdeploy.interfaces.variables.EnvironmentVariableResolver;
import io.bdeploy.interfaces.variables.EscapeJsonCharactersResolver;
import io.bdeploy.interfaces.variables.EscapeXmlCharactersResolver;
import io.bdeploy.interfaces.variables.EscapeYamlCharactersResolver;
import io.bdeploy.interfaces.variables.InstanceAndSystemVariableResolver;
import io.bdeploy.interfaces.variables.ManifestSelfResolver;
import io.bdeploy.interfaces.variables.OsVariableResolver;
import io.bdeploy.interfaces.variables.ParameterValueResolver;
import io.bdeploy.interfaces.variables.Resolvers;

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
     * A unique ID identifying this instance, even if the name changes on the
     * server. Used to calculate the location where things are deployed.
     * <p>
     * Redundant copy of the information from {@link InstanceConfiguration} for easier handling on the minion(s).
     */
    @JsonAlias("uuid")
    public String id;

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
     * Process control groups on this node.
     */
    public final List<ProcessControlGroupConfiguration> controlGroups = new ArrayList<>();

    /**
     * A merged map of variables available to the instance through both system and instance variables.
     */
    public final Map<String, String> variables = new TreeMap<>();

    /**
     * Render this {@link InstanceNodeConfiguration} to a {@link ProcessGroupConfiguration}
     * consumable by the PCU.
     *
     * @param valueResolver a resolver queried for all variables to expand.
     * @return the rendered {@link ProcessGroupConfiguration}, which is machine specific.
     */
    public ProcessGroupConfiguration renderDescriptor(VariableResolver valueResolver, InstanceNodeConfiguration inc) {
        ProcessGroupConfiguration pgc = new ProcessGroupConfiguration();
        pgc.name = name;
        pgc.id = id;
        pgc.autoStart = autoStart;

        // each downstream application has a scoped provider allowing to directly
        // reference parameter values of parameters in the same scope.
        for (ApplicationConfiguration app : applications) {
            ProcessConfiguration pc = app.renderDescriptor(Resolvers.forApplication(valueResolver, inc, app));
            pgc.applications.add(pc);
        }

        return pgc;
    }

    /**
     * Applies redundant copies of informative fields from the "parent" {@link InstanceConfiguration}
     */
    public void copyRedundantFields(InstanceConfiguration cfg) {
        id = cfg.id;
        name = cfg.name;
        autoStart = cfg.autoStart;
        purpose = cfg.purpose;
        product = cfg.product;
    }

    /**
     * @param instance the instance to pick variables from. instance variables have precedence over system variables.
     * @param system the system to pick variables from.
     * @param cfgResolver an optional resolver which will apply the given {@link VariableResolver} to all configuration files
     *            relevant for this node. if this is given, the resulting merge will *only* contain variables which are actually
     *            used. This must be done when persisting an instance node.
     * @implNote only variables which are actually used on this node shall be merged, since sensitive data may be present in
     *           variables!.
     */
    public void mergeVariables(InstanceConfiguration instance, SystemConfiguration system,
            Consumer<VariableResolver> cfgResolver) {
        variables.clear();

        // pick instance first, so system can overrule values later on.
        if (instance != null && instance.instanceVariables != null) {
            instance.instanceVariables.forEach(v -> {
                if (v.value != null) {
                    variables.put(v.id, v.value.getPreRenderable());
                }
            });
        }

        // now potentially overwrite instance variables with system ones.
        if (system != null && system.systemVariables != null) {
            system.systemVariables.forEach(v -> {
                if (v.value != null) {
                    variables.put(v.id, v.value.getPreRenderable());
                }
            });
        }

        if (cfgResolver != null) {
            // render the descriptor with as many resolvers as required, and a tracking resolver for variables, so we afterwards know which ones are required.
            TrackingInstanceAndSystemVariableResolver tracker = new TrackingInstanceAndSystemVariableResolver(this);
            CompositeResolver resolver = new CompositeResolver();
            resolver.add(tracker);
            resolver.add(Resolvers.forInstancePathIndependent(this));

            for (ApplicationConfiguration cfg : applications) {
                CompositeResolver perApp = Resolvers.forApplication(resolver, this, cfg);
                perApp.add(new EmptyVariableResolver()); // last one: ignore all other expansions.

                // this will update the tracking resolver with all required variables.
                cfg.renderDescriptor(perApp);

                // now also process all endpoints to get hold of what they need later
                for (HttpEndpoint ep : cfg.endpoints.http) {
                    CommonEndpointHelper.processEndpoint(perApp, ep);
                }
            }

            resolver.add(new EmptyVariableResolver()); // last one: ignore all other expansions
            cfgResolver.accept(resolver);

            // now we should have all variables collected.
            List<String> notRequired = variables.keySet().stream().filter(k -> !tracker.used.contains(k)).toList();
            notRequired.forEach(variables::remove);
        }
    }

    private static final class TrackingInstanceAndSystemVariableResolver extends InstanceAndSystemVariableResolver {

        private final Set<String> used = new TreeSet<>();

        public TrackingInstanceAndSystemVariableResolver(InstanceNodeConfiguration node) {
            super(node);
        }

        @Override
        protected String doResolve(String variable) {
            String val = super.doResolve(variable);
            if (val != null) {
                used.add(variable);
            }
            return val;
        }

    }
}
