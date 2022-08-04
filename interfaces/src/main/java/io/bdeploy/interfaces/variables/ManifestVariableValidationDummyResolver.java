package io.bdeploy.interfaces.variables;

/**
 * Resolves references to manifests. Always resolves to allow validation of expansions.
 */
public class ManifestVariableValidationDummyResolver extends PrefixResolver {

    public ManifestVariableValidationDummyResolver() {
        super(Variables.MANIFEST_REFERENCE);
    }

    @Override
    protected String doResolve(String variable) {
        return variable; // resolve to *something*.
    }

}
