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
        return unescaped.replace("'", "&apos;") // Replace ' with &apos;
                .replace("\"", "&quot;") // Replace " with &quot;
                .replace("&", "&amp;") // Replace & with &amp;
                .replace("<", "&lt;") // Replace < with &lt;
                .replace(">", "&gt;"); // Replace > with &gt;
    }

}
