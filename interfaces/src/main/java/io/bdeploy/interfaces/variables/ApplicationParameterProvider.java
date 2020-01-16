package io.bdeploy.interfaces.variables;

import java.util.Collection;
import java.util.stream.Collectors;

import com.google.common.collect.MoreCollectors;

import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
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
     * Returns the value of the given parameter defined in the application with the given display name.
     */
    public String getValueByDisplayName(String appName, String paramId) {
        ApplicationConfiguration appCfg = getAppByName(appName);
        return getParam(appCfg, paramId);
    }

    /**
     * Returns the value of the given parameter defined in the application with the given identifier.
     */
    public String getValueById(String appId, String paramId) {
        ApplicationConfiguration appCfg = getAppById(appId);
        return getParam(appCfg, paramId);
    }

    /**
     * Returns the application with the given name. Throws an exception when there are multiple applications with the same display
     * name.
     *
     * @param appName the name of the application
     * @return the value of the specified parameter.
     */
    public ApplicationConfiguration getAppByName(String appName) {
        Collection<ApplicationConfiguration> apps = config.applications.stream().filter(a -> a.name.equals(appName))
                .collect(Collectors.toList());
        if (apps.isEmpty()) {
            throw new RuntimeException("Unable to resolve parameter value. Application with the " + "name '" + appName
                    + "' is not existing in this instance.");
        }
        if (apps.size() != 1) {
            throw new RuntimeException("Unable to resolve parameter value. Variable references an application with the name '"
                    + appName + "' that is defined multiple times.");
        }
        return apps.iterator().next();
    }

    /**
     * Returns the application with the given identifier.
     *
     * @param appId the UID of the application
     * @return the value of the specified parameter.
     */
    public ApplicationConfiguration getAppById(String appId) {
        return config.applications.stream().filter(a -> a.uid.equals(appId)).collect(MoreCollectors.onlyElement());
    }

    public String getParam(ApplicationConfiguration app, String paramId) {
        return app.start.parameters.stream().filter(p -> p.uid.equals(paramId)).collect(MoreCollectors.onlyElement()).value;
    }

}
