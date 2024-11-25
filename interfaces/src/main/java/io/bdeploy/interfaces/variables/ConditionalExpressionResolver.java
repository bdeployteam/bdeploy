package io.bdeploy.interfaces.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.bdeploy.common.util.VariableResolver;

public class ConditionalExpressionResolver extends PrefixResolver {

    private static final Pattern EXPR_PATTERN = Pattern.compile("([^?]*)\\?([^:]*):(.*)");
    private final VariableResolver parent;

    /**
     * @param parent the resolver used to resolve the condition expression.
     */
    public ConditionalExpressionResolver(VariableResolver parent) {
        super(Variables.CONDITIONAL);
        this.parent = parent;
    }

    @Override
    protected String doResolve(String variable) {
        // format is "<expression>?<value-if-true>:<value-if-false>"
        // example: "X:instance-var?yay:oh-nooo"
        Matcher matcher = EXPR_PATTERN.matcher(variable);

        if (matcher.matches()) {
            String expr = matcher.group(1);
            String valueIfTrue = matcher.group(2);
            String valueIfFalse = matcher.group(3);

            String exprResult = parent.apply(expr);

            // don't use boolean parsing. we want "null", "empty", and "false" to be false, all others are true.
            if (exprResult != null && !exprResult.isBlank() && !"false".equalsIgnoreCase(exprResult)) {
                return valueIfTrue;
            } else {
                return valueIfFalse;
            }
        }

        // not resolvable.
        return null;
    }

}
