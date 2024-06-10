package io.bdeploy.interfaces.variables;

import io.bdeploy.common.util.VariableResolver;

/**
 * Resolves variables starting with the XML: keyword by escaping special characters and making resulting string XML-friendly
 */
public class EscapeXmlCharactersResolver extends PrefixResolver {

    private final VariableResolver parentResolver;

    public EscapeXmlCharactersResolver(VariableResolver parentResolver) {
        super(Variables.ESCAPE_XML);
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
        return unescaped.replace("'", "&apos;") //
                .replace("\"", "&quot;") //
                .replace("&", "&amp;") //
                .replace("<", "&lt;") //
                .replace(">", "&gt;"); //
    }

}
