package io.bdeploy.interfaces.variables;

import java.util.List;
import java.util.Optional;

import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;

public class FixedParameterListValueResolver extends PrefixResolver {

    private final List<ParameterConfiguration> parameters;

    public FixedParameterListValueResolver(List<ParameterConfiguration> parameters) {
        super(Variables.PARAMETER_VALUE);

        this.parameters = parameters;
    }

    @Override
    protected String doResolve(String variable) {
        if (variable.contains(":")) {
            return null; // scoped is non of our business;
        }

        Optional<ParameterConfiguration> match = parameters.stream().filter(p -> p.id.equals(variable)).findAny();

        if (match.isPresent()) {
            return match.get().value.getPreRenderable();
        }

        return null;
    }

}
