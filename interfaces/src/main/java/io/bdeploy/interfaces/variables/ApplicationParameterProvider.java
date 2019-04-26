package io.bdeploy.interfaces.variables;

import static io.bdeploy.common.util.RuntimeAssert.assertTrue;

import java.util.List;

import com.google.common.base.Splitter;
import com.google.common.collect.MoreCollectors;

import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;

/**
 * Handles parameter cross-references in parameter values.
 */
public class ApplicationParameterProvider {

    private final InstanceNodeConfiguration config;

    public ApplicationParameterProvider(InstanceNodeConfiguration config) {
        this.config = config;
    }

    /**
     * @param name the scoped name of the parameter as in "app name:param-id"
     * @return the value of the specified parameter.
     */
    public String getParameterValue(String name) {
        List<String> appAndParameter = Splitter.on(':').splitToList(name);
        assertTrue(appAndParameter.size() == 2, "Illegal parameter reference: " + name);

        // TODO: support integer arithmetic for ports, ...

        String app = appAndParameter.get(0);
        String parameter = appAndParameter.get(1);

        ParameterConfiguration match = config.applications.stream().filter(a -> a.name.equals(app))
                .flatMap(a -> a.start.parameters.stream()).filter(p -> p.uid.equals(parameter))
                .collect(MoreCollectors.onlyElement());

        return match.value;
    }

}
