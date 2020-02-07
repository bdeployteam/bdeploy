package io.bdeploy.interfaces.variables;

import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;

/**
 * A variable resolver capable to resolve instance specific variables.
 */
public class InstanceVariableResolver extends PrefixResolver {

    private final InstanceNodeConfiguration incf;

    public InstanceVariableResolver(InstanceNodeConfiguration incf) {
        super(Variables.INSTANCE_VALUE);
        this.incf = incf;
    }

    @Override
    protected String doResolve(String variable) {
        switch (variable) {
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

}
