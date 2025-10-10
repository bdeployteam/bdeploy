package io.bdeploy.interfaces.variables;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import io.bdeploy.common.util.VariableResolver;

/**
 * Resolves variables starting with the FILEURI: keyword by transforming them into file URIs.
 */
public class FileUriResolver extends PrefixResolver {

    private final VariableResolver parentResolver;

    public FileUriResolver(VariableResolver parentResolver) {
        super(Variables.FILEURI);
        this.parentResolver = parentResolver;
    }

    @Override
    protected String doResolve(String variable) {
        var parentResult = parentResolver.apply(variable);
        if (parentResult == null) {
            return null;
        }
        Path p;
        try {
            p = Path.of(parentResult);
        } catch (InvalidPathException e) {
            return null;
        }
        return p.toUri().toString();
    }
}
