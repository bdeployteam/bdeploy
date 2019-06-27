package io.bdeploy.minion;

import java.util.function.UnaryOperator;

/**
 * An additional variable resolver used by the DCU to resolve variables which
 * are specific to the Minion configuration.
 */
public class MinionConfigVariableResolver implements UnaryOperator<String> {

    private final MinionRoot root;

    public MinionConfigVariableResolver(MinionRoot root) {
        this.root = root;
    }

    @Override
    public String apply(String t) {
        if (t.startsWith("H:")) {
            String var = t.substring(2);
            switch (var) {
                case "HOSTNAME":
                    return root.getState().officialName;
                default:
                    return null;
            }
        }
        return null;
    }

}
