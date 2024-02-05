package io.bdeploy.interfaces.variables;

import io.bdeploy.common.util.VariableResolver;

/**
 * Resolves variables starting with the JSON: keyword by escaping special characters and making resulting string JSON-friendly
 */
public class EscapeJsonCharactersResolver extends PrefixResolver {

    private final VariableResolver parentResolver;

    public EscapeJsonCharactersResolver(VariableResolver parentResolver) {
        super(Variables.ESCAPE_JSON);
        this.parentResolver = parentResolver;
    }

    @Override
    protected String doResolve(String variable) {
        return escape(parentResolver.apply(variable));
    }

    private String escape(String unescaped) {
        if (unescaped == null) {
            return null;
        }
        return unescaped.replace("\b", "\\b") // Backspace is replaced with \b
                .replace("\f", "\\f") // Form feed is replaced with \f
                .replace("\n", "\\n") // Newline is replaced with \n
                .replace("\r", "\\r") // Carriage return is replaced with \r
                .replace("\t", "\\t") // Tab is replaced with \t
                .replace("\"", "\\\"") // Double quote is replaced with \"
                .replace("\\", "\\\\"); // Backslash is replaced with \\
    }

}
