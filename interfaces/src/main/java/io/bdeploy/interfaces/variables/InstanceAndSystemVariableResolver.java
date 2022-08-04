package io.bdeploy.interfaces.variables;

import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;

/**
 * Resolves variable values from the merged instance and system variables redundantly stored on the instance node configuration.
 */
public class InstanceAndSystemVariableResolver extends PrefixResolver {

    private final InstanceNodeConfiguration node;

    public InstanceAndSystemVariableResolver(InstanceNodeConfiguration node) {
        super(Variables.SYSTEM_INSTANCE_VARIABLE);

        this.node = node;
    }

    @Override
    protected String doResolve(String variable) {
        return node.variables.get(variable);
    }

}
