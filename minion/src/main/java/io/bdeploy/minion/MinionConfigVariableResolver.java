package io.bdeploy.minion;

import io.bdeploy.interfaces.variables.PrefixResolver;
import io.bdeploy.interfaces.variables.Variables;

/**
 * An additional variable resolver used by the DCU to resolve variables which are specific to the Minion configuration.
 */
public class MinionConfigVariableResolver extends PrefixResolver {

    private final MinionRoot root;

    public MinionConfigVariableResolver(MinionRoot root) {
        super(Variables.HOST);
        this.root = root;
    }

    @Override
    protected String doResolve(String variable) {
        if ("HOSTNAME".equals(variable)) {
            return root.getState().officialName;
        }
        return null;
    }

}
