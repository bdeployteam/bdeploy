package io.bdeploy.interfaces.variables;

public class ManifestVariableDummyResolver extends PrefixResolver {

    public ManifestVariableDummyResolver() {
        super(Variables.MANIFEST_REFERENCE);
    }

    @Override
    protected String doResolve(String variable) {
        return variable; // resolve to *something*.
    }
}
