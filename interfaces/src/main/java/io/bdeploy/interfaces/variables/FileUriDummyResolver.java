package io.bdeploy.interfaces.variables;

import io.bdeploy.common.util.VariableResolver;

/**
 * Dummy variant of {@link FileUriResolver} that never throws an exception.
 */
public class FileUriDummyResolver extends PrefixResolver {

    private final VariableResolver parentResolver;

    public FileUriDummyResolver(VariableResolver parentResolver) {
        super(Variables.FILEURI);
        this.parentResolver = parentResolver;
    }

    @Override
    protected String doResolve(String variable) {
        var parentResult = parentResolver.apply(variable);
        return parentResult == null ? null : "file://" + parentResult;
    }
}
