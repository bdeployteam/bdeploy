package io.bdeploy.interfaces.configuration.dcu;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor.ApplicationPoolType;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint.HttpEndpointType;
import io.bdeploy.interfaces.variables.SkipDelayed;
import io.bdeploy.interfaces.variables.Variables;

/**
 * Holds the configuration for an application ({@link #application}) as created
 * by a configuration UI
 */
public class ApplicationConfiguration {

    /**
     * Globally unique identifier of the application.
     */
    @JsonAlias("uid")
    public String id;

    /**
     * @deprecated Compat with 4.x
     */
    @Deprecated(forRemoval = true)
    @JsonProperty("uid")
    public String getUid() {
        return id;
    }

    /**
     * The human readable name of the application configuration (e.g. "My App 1").
     */
    public String name;

    /**
     * Reference to the actual application {@link Manifest} which contains the
     * {@link ApplicationDescriptor}.
     */
    public Manifest.Key application;

    /**
     * The pooling type for this process. Always the same as the pooling type of the application.
     */
    public ApplicationPoolType pooling;

    /**
     * Process control information.
     */
    public ProcessControlConfiguration processControl;

    /**
     * Pre-rendered start command.
     */
    public CommandConfiguration start;

    /**
     * Pre-rendered stop command.
     */
    public CommandConfiguration stop;

    /**
     * Available/configured endpoints for this application.
     */
    public EndpointsConfiguration endpoints = new EndpointsConfiguration();

    /**
     * Create a PCU-compliant {@link ProcessConfiguration}, resolving
     * values from the given resolver.
     * <p>
     * This happens after deploying applications, when all paths for all deployed
     * artifacts are existing and fixed.
     */
    public ProcessConfiguration renderDescriptor(VariableResolver valueResolver) {
        SkipDelayed skipDelayCallback = new SkipDelayed();
        ProcessConfiguration processConfig = new ProcessConfiguration();

        processConfig.id = id;
        processConfig.name = name;
        processConfig.processControl = processControl;

        for (HttpEndpoint ep : endpoints.http) {
            if (ep.type == HttpEndpointType.PROBE_ALIVE || ep.type == HttpEndpointType.PROBE_STARTUP) {
                processConfig.endpoints.http.add(ep);
            }
        }

        String appManifestPath = Variables.MANIFEST_REFERENCE.format(application.toString());
        String path = valueResolver.apply(appManifestPath);
        if (path == null) {
            throw new IllegalStateException("Unable to determine application installation path. Reference=" + appManifestPath);
        }
        Path manifestInstallPath = Paths.get(path);

        if (start.executable == null) {
            throw new IllegalStateException("No executable set for application '" + name + "' (" + id + ")");
        }

        String startCmd = TemplateHelper.process(start.executable, valueResolver, skipDelayCallback);
        processConfig.start.add(manifestInstallPath.resolve(startCmd).toString());
        start.parameters.stream().map(pc -> TemplateHelper.process(pc.preRendered, valueResolver, skipDelayCallback))
                .forEach(processConfig.start::addAll);

        if (hasStopCommand()) {
            String stopCmd = TemplateHelper.process(stop.executable, valueResolver, skipDelayCallback);
            processConfig.stop.add(manifestInstallPath.resolve(stopCmd).toString());
            stop.parameters.stream().map(pc -> TemplateHelper.process(pc.preRendered, valueResolver, skipDelayCallback))
                    .forEach(processConfig.stop::addAll);
        }

        return processConfig;
    }

    private boolean hasStopCommand() {
        return stop != null && stop.executable != null && !stop.executable.isEmpty();
    }

}
