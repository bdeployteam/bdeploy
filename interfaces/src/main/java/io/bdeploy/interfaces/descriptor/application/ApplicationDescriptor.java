package io.bdeploy.interfaces.descriptor.application;

import javax.annotation.processing.Generated;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import io.bdeploy.api.product.v1.ApplicationDescriptorApi;

/**
 * Top level element defining an application. The serialized form of this DTO
 * <b>must</b> be present for any application which wishes to be controlled by
 * the PCU.
 * <p>
 * The serialized form of this DTO must reside in the top-level or any
 * application imported into the system, and have the name {@value #FILE_NAME}.
 */
public class ApplicationDescriptor extends ApplicationDescriptorApi implements Comparable<ApplicationDescriptor> {

    public enum ApplicationType {
        @JsonEnumDefaultValue
        SERVER,
        CLIENT;
    }

    /**
     * User friendly name of the application
     */
    public String name;

    /**
     * Type of application.
     */
    public ApplicationType type = ApplicationType.SERVER;

    /**
     * Provides information about exit codes of the application.
     */
    public ApplicationExitCodeDescriptor exitCodes = new ApplicationExitCodeDescriptor();

    /**
     * Provides branding information (splash, icon, ...).
     * <p>
     * This information is evaluated and used for instance by the client launcher, possibly by others.
     */
    public ApplicationBrandingDescriptor branding = new ApplicationBrandingDescriptor();

    /**
     * Describes the process control specific properties of this application.
     */
    public ProcessControlDescriptor processControl = new ProcessControlDescriptor();

    /**
     * Description of command used to start an application.
     */
    public ExecutableDescriptor startCommand;

    /**
     * Description of command used to stop and application. This is optional. If not
     * given, the existing process is destroyed (which usually gives it chance to
     * shut down properly).
     */
    public ExecutableDescriptor stopCommand;

    /**
     * All endpoints which are provided by the application.
     */
    public EndpointsDescriptor endpoints = new EndpointsDescriptor();

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
