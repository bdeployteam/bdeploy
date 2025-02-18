package io.bdeploy.interfaces.variables;

/**
 * Resolves references to manifests. Always resolves to allow validation of expansions.
 */
public class ManifestVariableDummyResolver extends PrefixResolver {

    public ManifestVariableDummyResolver() {
        super(Variables.MANIFEST_REFERENCE);
    }

    @Override
    protected String doResolve(String variable) {
        return variable; // resolve to *something*.
    }

}
