package io.bdeploy.interfaces.variables;

import java.util.function.Function;

import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.variables.VariableResolver.SpecialVariablePrefix;

/**
 * An additional variable resolver used by the DCU to resolve variables which
 * are specific to the instance configuration.
 */
public class InstanceVariableResolver implements Function<String, String> {

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
                    return "DEVELOPMENT"; // TODO: actual values from INMF
                case "UUID":
                    return incf.uuid;
                case "NAME":
                    return incf.name;
            }
        }
        return null;
    }

}
