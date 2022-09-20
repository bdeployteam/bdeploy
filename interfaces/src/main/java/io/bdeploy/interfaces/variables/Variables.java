package io.bdeploy.interfaces.variables;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;

/**
 * A list of all variables that are supported by the application.
 */
public enum Variables {

    /**
     * Variable has a manifest reference. The value is expected to be a
     * {@link Manifest} name and optionally a tag separated by ':'
     */
    MANIFEST_REFERENCE("M:"),

    /**
     * Variable references one of the {@link SpecialDirectory} directories. The
     * value is expected to match one of the {@link SpecialDirectory} enumeration
     * literals (looked up using valueOf).
     */
    DEPLOYMENT_PATH("P:"),

    /**
     * Variable references a parameter in any application contained in the same
     * deployment. The value is expected to contain the referenced application name
     * and the parameter id separated by ':' (e.g. "MyApp:param1").
     */
    PARAMETER_VALUE("V:"),

    /**
     * A value which is provided by the enclosing instance.
     */
    INSTANCE_VALUE("I:"),

    /**
     * A value which is provided by the enclosing application.
     */
    APP_VALUE("A:"),

    /**
     * A value which is provided by system or instance variables.
     */
    SYSTEM_INSTANCE_VARIABLE("X:"),

    /**
     * References an environmental variable of the operating system. The value
     * is the name of the variable as present in the OS.
     */
    ENVIRONMENT_VARIABLE("ENV:"),

    /**
     * Indicates that the enclosed variable should be resolved on startup of the application. The content can be any of the
     * other variables
     * defined in here.
     *
     * <pre>
     *      {{DELAYED:ENV:JAVA_HOME}}   -   Delayed resolving of the environment variable JAVA_HOME
     * </pre>
     */
    DELAYED("DELAYED:"),

    /**
     * A value that is resolved on the host where the application is running.
     */
    HOST("H:"),

    /**
     * A conditional which evaluates a nested expression (must be boolean), and evaluates to one of two given values.
     */
    CONDITIONAL("IF:");

    private final String prefix;

    private Variables(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Formats a reference to the given value for this type.
     */
    public String format(String string) {
        return this.prefix + string;
    }

    public String getPrefix() {
        return this.prefix;
    }
}