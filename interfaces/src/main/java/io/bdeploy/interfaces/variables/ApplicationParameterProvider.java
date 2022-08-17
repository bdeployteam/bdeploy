package io.bdeploy.interfaces.variables;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.MoreCollectors;

import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
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
        Collection<ApplicationConfiguration> apps = config.applications.stream().filter(a -> a.name.equals(appName)).toList();
        if (apps.isEmpty()) {
            throw new IllegalArgumentException("Unable to resolve parameter value. Application with the " + "name '" + appName
                    + "' is not existing in this instance.");
        }
        if (apps.size() != 1) {
            throw new IllegalArgumentException(
                    "Unable to resolve parameter value. Variable references an application with the name '" + appName
                            + "' that is defined multiple times.");
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
        List<ParameterConfiguration> params = app.start.parameters.stream().filter(p -> p.uid.equals(paramId)).toList();
        if (params.size() != 1) {
            throw new IllegalArgumentException(
                    "Cannot find unique parameter " + paramId + " for application " + app.name + ", found " + params.size());
        }

        if (params.get(0).value == null) {
            return null; // no actual value (yet).
        }

        // IF the value is a parameter reference, we need to make sure it is scoped to the current application!
        return TemplateHelper.updateReferences(params.get(0).value.getPreRenderable(), r -> updateReference(app, r));
    }

    private String updateReference(ApplicationConfiguration app, String templateRef) {
        if (templateRef != null && templateRef.startsWith(Variables.PARAMETER_VALUE.getPrefix())) {
            String actualRef = templateRef.substring(Variables.PARAMETER_VALUE.getPrefix().length());

            if (!actualRef.contains(":")) {
                // unscoped, scope
                return Variables.PARAMETER_VALUE.getPrefix() + app.name + ':' + actualRef;
            }
        }

        return templateRef; // leave unmodified.
    }

}
