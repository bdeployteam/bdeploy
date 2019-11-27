package io.bdeploy.interfaces.variables;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;

/**
 * Resolves variables of different types.
 *
 * @see SpecialVariablePrefix
 */
public class VariableResolver implements UnaryOperator<String> {

    public enum SpecialVariablePrefix {

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
        INSTANCE_VALUE("I:");

        private final String prefix;

        private SpecialVariablePrefix(String prefix) {
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

    private final DeploymentPathProvider paths;
    private final ManifestRefPathProvider manifests;
    private final ApplicationParameterProvider parameters;
    private final List<UnaryOperator<String>> additionalResolvers;

    public VariableResolver(DeploymentPathProvider paths, ManifestRefPathProvider manifests,
            ApplicationParameterProvider parameters, List<UnaryOperator<String>> additionalResolvers) {
        this.paths = paths;
        this.manifests = manifests;
        this.parameters = parameters;
        this.additionalResolvers = new ArrayList<>(additionalResolvers);

        // built-in additional resolver(s).
        this.additionalResolvers.add(new OsConditionalProvider());
    }

    @Override
    public String apply(String varName) {
        String v = getVariableValue(SpecialVariablePrefix.MANIFEST_REFERENCE, varName);
        if (v != null) {
            Path manifestPath = manifests.getManifestPath(v);
            if (manifestPath == null) {
                throw new IllegalStateException("Cannot resolve: " + v);
            }
            return manifestPath.toString();
        }

        v = getVariableValue(SpecialVariablePrefix.DEPLOYMENT_PATH, varName);
        if (v != null) {
            return paths.get(SpecialDirectory.valueOf(v)).toString();
        }

        v = getVariableValue(SpecialVariablePrefix.PARAMETER_VALUE, varName);
        if (v != null) {
            return parameters.getParameterValue(v);
        }

        for (UnaryOperator<String> resolver : additionalResolvers) {
            v = resolver.apply(varName);
            if (v != null) {
                return v;
            }
        }

        throw new IllegalArgumentException("Unresolved variable: " + varName);
    }

    private String getVariableValue(SpecialVariablePrefix which, String raw) {
        if (raw.startsWith(which.prefix)) {
            return raw.substring(which.prefix.length());
        }
        return null;
    }

    public UnaryOperator<String> scopedTo(String application, Manifest.Key manifest) {
        return s -> {
            String v = getVariableValue(SpecialVariablePrefix.PARAMETER_VALUE, s);
            if (v != null && !v.contains(":")) {
                return apply(SpecialVariablePrefix.PARAMETER_VALUE.format(application + ":" + v));
            }
            v = getVariableValue(SpecialVariablePrefix.MANIFEST_REFERENCE, s);
            if (v != null && v.equals("SELF")) {
                return apply(SpecialVariablePrefix.MANIFEST_REFERENCE.format(manifest.toString()));
            }
            return apply(s);
        };
    }

}
