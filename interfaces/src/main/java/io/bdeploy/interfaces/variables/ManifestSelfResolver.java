package io.bdeploy.interfaces.variables;

import java.util.function.UnaryOperator;

import io.bdeploy.bhive.model.Manifest;

/**
 * Replaces the 'SELF' entry with the given manifest key.
 */
public class ManifestSelfResolver extends PrefixResolver {

    private final Manifest.Key manifestKey;
    private final UnaryOperator<String> parentResolver;

    public ManifestSelfResolver(Manifest.Key manifestKey, UnaryOperator<String> parentResolver) {
        super(Variables.MANIFEST_REFERENCE);
        this.manifestKey = manifestKey;
        this.parentResolver = parentResolver;
    }

    @Override
    protected String doResolve(String variable) {
        if (variable.equals("SELF")) {
            String scoped = prefix.format(manifestKey.toString());
            return parentResolver.apply(scoped);
        }
        return null;
    }

}
