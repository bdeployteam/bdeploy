package io.bdeploy.interfaces.configuration.dcu;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.UnaryOperator;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.variables.VariableResolver.SpecialVariablePrefix;

/**
 * Holds the configuration for an application ({@link #application}) as created
 * by a configuration UI
 */
public class ApplicationConfiguration {

    /**
     * Globally unique identifier of the application.
     */
    public String uid;

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
     * Create a PCU-compliant {@link ProcessConfiguration}, resolving
     * values from the given resolver.
     * <p>
     * This happens after deploying applications, when all paths for all deployed
     * artifacts are existing and fixed.
     */
    public ProcessConfiguration renderDescriptor(UnaryOperator<String> valueResolver) {
        ProcessConfiguration add = new ProcessConfiguration();

        add.uid = uid;
        add.name = name;
        add.processControl = processControl;

        Path manifestInstallPath = Paths
                .get(valueResolver.apply(SpecialVariablePrefix.MANIFEST_REFERENCE.format(application.toString())));

        if (start.executable == null) {
            throw new IllegalStateException("No executable set for application '" + name + "' (" + uid + ")");
        }

        add.start.add(manifestInstallPath.resolve(ParameterConfiguration.process(start.executable, valueResolver)).toString());
        start.parameters.stream().map(pc -> pc.renderDescriptor(valueResolver)).forEach(add.start::addAll);

        if (hasStopCommand()) {
            add.stop.add(manifestInstallPath.resolve(ParameterConfiguration.process(stop.executable, valueResolver)).toString());
            stop.parameters.stream().map(pc -> pc.renderDescriptor(valueResolver)).forEach(add.stop::addAll);
        }

        return add;
    }

    private boolean hasStopCommand() {
        return stop != null && stop.executable != null && !stop.executable.isEmpty();
    }

}
