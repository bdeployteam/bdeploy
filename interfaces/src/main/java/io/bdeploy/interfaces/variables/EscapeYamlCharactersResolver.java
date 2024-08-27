package io.bdeploy.interfaces.variables;

import java.util.Set;

import io.bdeploy.common.util.VariableResolver;

/**
 * Resolves variables starting with the YAML: keyword by escaping special characters and making resulting string YAML-friendly
 */
public class EscapeYamlCharactersResolver extends PrefixResolver {

    private static final Set<String> SPECIAL_CHARACTERS = Set.of("\\", ":", ";", "_", "(", ")", "@", "$", "%", "^", "&", ",", " ",
            "'", "\"");

    private final VariableResolver parentResolver;

    public EscapeYamlCharactersResolver(VariableResolver parentResolver) {
        super(Variables.ESCAPE_YAML);
        this.parentResolver = parentResolver;
    }

    @Override
    protected String doResolve(String variable) {
        return escape(parentResolver.apply(variable));
    }

    private static String escape(String unescaped) {
        if (unescaped == null) {
            return null;
        }

        boolean hasSpecialCharacters = false;
        for (String special : SPECIAL_CHARACTERS) {
            if (unescaped.contains(special)) {
                hasSpecialCharacters = true;
                break;
            }
        }

        if (hasSpecialCharacters) {
            return "\"" + unescaped.replace("\"", "\\\"") + "\"";
        } else {
            return unescaped;
        }
    }

}
