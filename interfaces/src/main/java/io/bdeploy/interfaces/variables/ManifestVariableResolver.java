package io.bdeploy.interfaces.variables;

import java.nio.file.Path;

/**
 * Resolves references to manifests.
 */
public class ManifestVariableResolver extends PrefixResolver {

    private final ManifestRefPathProvider manifests;

    public ManifestVariableResolver(ManifestRefPathProvider manifests) {
        super(Variables.MANIFEST_REFERENCE);
        this.manifests = manifests;
    }

    @Override
    protected String doResolve(String variable) {
        Path manifestPath = manifests.getManifestPath(variable);
        if (manifestPath == null) {
            throw new IllegalStateException("Cannot resolve: " + variable);
        }
        return manifestPath.toString();
    }

}
