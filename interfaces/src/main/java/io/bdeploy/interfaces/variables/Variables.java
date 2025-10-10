package io.bdeploy.interfaces.variables;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.util.ShouldResolve;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;

/**
 * A list of all variables that are supported by the application.
 */
public enum Variables {

    /**
     * Only used in templates.
     */
    TEMPLATE("T:", true),

    /**
     * Variable has a manifest reference. The value is expected to be a
     * {@link Manifest} name and optionally a tag separated by ':'
     */
    MANIFEST_REFERENCE("M:", false),

    /**
     * Variable references one of the {@link SpecialDirectory} directories. The
     * value is expected to match one of the {@link SpecialDirectory} enumeration
     * literals (looked up using valueOf).
     */
    DEPLOYMENT_PATH("P:", false),

    /**
     * Variable references a parameter in any application contained in the same
     * deployment. The value is expected to contain the referenced application name
     * and the parameter id separated by ':' (e.g. "MyApp:param1").
     */
    PARAMETER_VALUE("V:", true),

    /**
     * A value which is provided by the enclosing instance.
     */
    INSTANCE_VALUE("I:", false),

    /**
     * A value which is provided by the enclosing application.
     */
    APP_VALUE("A:", false),

    /**
     * A value which is provided by system or instance variables.
     */
    SYSTEM_INSTANCE_VARIABLE("X:", true),

    /**
     * References an environmental variable of the operating system. The value
     * is the name of the variable as present in the OS.
     */
    ENVIRONMENT_VARIABLE("ENV:", false),//TODO arithmetics should be supported - but we have to support baseline numeric environment variables first

    /**
     * Indicates that the enclosed variable should be resolved on startup of the application. The content can be any of the
     * other variables
     * defined in here.
     *
     * <pre>
     *      {{DELAYED:ENV:JAVA_HOME}}   -   Delayed resolving of the environment variable JAVA_HOME
     * </pre>
     */
    DELAYED("DELAYED:", false),

    /**
     * A value that is resolved on the host where the application is running.
     */
    HOST("H:", false),

    /**
     * Transforms the given path into a file URI.
     */
    FILEURI("FILEURI:", false),

    /**
     * A conditional which evaluates a nested expression (must be boolean), and evaluates to one of two given values.
     */
    CONDITIONAL("IF:", false),

    /**
     * Escapes characters that could corrupt XML files
     */
    ESCAPE_XML("XML:", false),

    /**
     * Escapes characters that could corrupt JSON files
     */
    ESCAPE_JSON("JSON:", false),

    /**
     * Escapes characters that could corrupt YAML files
     */
    ESCAPE_YAML("YAML:", false)

    ;

    private final String prefix;
    private final boolean arithmeticAllowed;

    private Variables(String prefix, boolean arithmeticAllowed) {
        this.prefix = prefix;
        this.arithmeticAllowed = arithmeticAllowed;
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

    public boolean isArithmeticAllowed() {
        return this.arithmeticAllowed;
    }

    public ShouldResolve shouldResolve() {
        return s -> s.startsWith(prefix);
    }
}
