package io.bdeploy.interfaces.descriptor.application;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.processing.Generated;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.google.common.collect.ImmutableList;

import io.bdeploy.api.product.v1.ApplicationDescriptorApi;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor.ParameterType;
import io.bdeploy.interfaces.descriptor.template.ParameterTemplateDescriptor;
import io.bdeploy.interfaces.manifest.ProductManifest;

/**
 * Top level element defining an application. The serialized form of this DTO
 * <b>must</b> be present for any application which wishes to be controlled by
 * the PCU.
 * <p>
 * The serialized form of this DTO must reside in the top-level or any
 * application imported into the system, and have the name {@value #FILE_NAME}.
 */
@JsonClassDescription("Describes an application, and all its properties.")
public class ApplicationDescriptor extends ApplicationDescriptorApi implements Comparable<ApplicationDescriptor> {

    private static final Logger log = LoggerFactory.getLogger(ApplicationDescriptor.class);

    /**
     * The type of application described.
     */
    @JsonClassDescription("Whether the application is meant to run on a server node, or through the launcher on a client.")
    public enum ApplicationType {
        @JsonEnumDefaultValue
        SERVER,
        CLIENT;
    }

    /**
     * The type of pooling applicable to this application.
     * <p>
     * <ol>
     * <li>GLOBAL: Application can be installed once for a given version, and be used by multiple instances, and multiple instance
     * versions therein.</li>
     * <li>LOCAL: Application can be installed once per instance, and will be reused by multiple versions of this instance
     * only</li>
     * <li>NONE: No pooling, the application is reinstalled for every instance version, even for minor configuration changed</li>
     * </ol>
     */
    @JsonClassDescription("Defines how strongly pooled the application can be in an installation. Applications that write data in their own installation directory should generally not be pooled.")
    public enum ApplicationPoolType {
        @JsonEnumDefaultValue
        GLOBAL,
        LOCAL,
        NONE
    }

    @JsonPropertyDescription("The name of the application.")
    public String name;

    @JsonPropertyDescription("The type of the application, defaults to SERVER")
    public ApplicationType type = ApplicationType.SERVER;

    @JsonPropertyDescription("The pooling policy for this application, defaults to GLOBAL")
    public ApplicationPoolType pooling = ApplicationPoolType.GLOBAL;

    @JsonPropertyDescription("Defines application specific well known exit codes.")
    public ApplicationExitCodeDescriptor exitCodes = new ApplicationExitCodeDescriptor();

    @JsonPropertyDescription("Defines branding of a client application.")
    public ApplicationBrandingDescriptor branding = new ApplicationBrandingDescriptor();

    @JsonPropertyDescription("Defines the process control specific properties of this application.")
    public ProcessControlDescriptor processControl = new ProcessControlDescriptor();

    @JsonPropertyDescription("Description of possible individual components of the command used to start this application.")
    public ExecutableDescriptor startCommand;

    @JsonPropertyDescription("Description of possible individual components of the command used to stop this application. If not configured, the application will be stopped using OS mechanisms instead.")
    public ExecutableDescriptor stopCommand;

    @JsonPropertyDescription("Describes all endpoints which are provided by the application. Those endpoints can then be used via BDeploys proxy feature or as UI endpoints.")
    public EndpointsDescriptor endpoints = new EndpointsDescriptor();

    /**
     * Some combinations of settings are invalid. Fix them up, so defaults change depending in the settings.
     * <p>
     * The first and currently only expample is a parameter with 'hasValue = false'. This should implicitly
     * make the parameter BOOLEAN (present or not present). Another type is not allowed.
     */
    public void fixupDefaults() {
        if (startCommand != null) {
            fixupParameterDefaults(startCommand.parameters);
        }

        if (stopCommand != null) {
            fixupParameterDefaults(stopCommand.parameters);
        }
    }

    private void fixupParameterDefaults(Collection<ParameterDescriptor> params) {
        for (var param : params) {
            if (!param.hasValue) {
                param.type = ParameterType.BOOLEAN;
            }
        }
    }

    public void fixupParameterExpansion(ProductManifest pm) {
        List<ParameterTemplateDescriptor> templates = pm.getParameterTemplates();

        if (templates == null) {
            // in case of deserialization from old version.
            templates = Collections.emptyList();
        }

        // replace each parameter definition which uses "shared" with the according shared block from the product
        if (startCommand != null) {
            fixupCommandExpansion(templates, startCommand.parameters);
        }

        if (stopCommand != null) {
            fixupCommandExpansion(templates, stopCommand.parameters);
        }
    }

    private void fixupCommandExpansion(List<ParameterTemplateDescriptor> templates, List<ParameterDescriptor> params) {
        ParameterDescriptor toReplace = null;
        do {
            toReplace = params.stream().filter(p -> p.template != null).findFirst().orElse(null);
            if (toReplace != null) {
                // replace/expand it.
                int index = params.indexOf(toReplace);
                params.remove(index); // remove the original element.

                String templateId = toReplace.template;
                List<ParameterDescriptor> replacements = templates.stream().filter(t -> t.id.equals(templateId))
                        .map(t -> t.parameters).findFirst().orElse(null);

                if (replacements == null) {
                    log.warn("No shared parameter replacement found for " + templateId);
                } else {
                    ImmutableList.copyOf(replacements).reverse().forEach(r -> params.add(index, r));
                }
            }
        } while (toReplace != null);
    }

    @Override
    public int compareTo(ApplicationDescriptor o) {
        return name.compareTo(o.name);
    }

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Generated("Eclipse")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ApplicationDescriptor other = (ApplicationDescriptor) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

}
