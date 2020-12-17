package io.bdeploy.interfaces.variables;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import io.bdeploy.common.util.VariableResolver;

/**
 * Represents a list of resolvers that are called in the registered order until one can resolve the value.
 */
public class CompositeResolver implements VariableResolver {

    private final List<VariableResolver> resolvers = new ArrayList<>();

    @Override
    public String apply(String varName) {
        for (UnaryOperator<String> resolver : resolvers) {
            String v = resolver.apply(varName);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /**
     * Adds an additional resolver to the list of resolvers
     */
    public void add(VariableResolver resolver) {
        this.resolvers.add(resolver);
    }

}
