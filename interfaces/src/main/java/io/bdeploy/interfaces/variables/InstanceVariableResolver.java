package io.bdeploy.interfaces.variables;

import java.util.function.UnaryOperator;

import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.variables.VariableResolver.SpecialVariablePrefix;

/**
 * An additional variable resolver used by the DCU to resolve variables which
 * are specific to the instance configuration.
 */
public class InstanceVariableResolver implements UnaryOperator<String> {

    private final InstanceNodeConfiguration incf;

    public InstanceVariableResolver(InstanceNodeConfiguration incf) {
        this.incf = incf;
    }

    @Override
    public String apply(String t) {
        if (t.startsWith(SpecialVariablePrefix.INSTANCE_VALUE.getPrefix())) {
            String var = t.substring(SpecialVariablePrefix.INSTANCE_VALUE.getPrefix().length());
            switch (var) {
                case "SYSTEM_PURPOSE":
                    return incf.purpose == null ? "" : incf.purpose.name();
                case "UUID":
                    return incf.uuid;
                case "NAME":
                    return incf.name;
                case "PRODUCT_ID":
                    return incf.product == null ? "" : incf.product.getName();
                case "PRODUCT_TAG":
                    return incf.product == null ? "" : incf.product.getTag();
                default:
                    return null;
            }
        }
        return null;
    }

}
