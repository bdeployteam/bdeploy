package io.bdeploy.interfaces.descriptor.client;

import java.util.Objects;

import io.bdeploy.common.security.RemoteService;

/**
 * Describes the content of the {@literal Click & Start} file.
 * <p>
 * The file only contains immutable data which is required to uniquely identify
 * the application on the hosting system. All other information might be changed
 * later by configuration and thus must be loaded every time (human readable
 * name, icon, ...).
 */
public class ClickAndStartDescriptor {

    /**
     * Host to fetch client data from.
     */
    public RemoteService host;

    /**
     * Instance Group ID
     */
    public String groupId;

    /**
     * Instance ID
     */
    public String instanceId;

    /**
     * Application ID (ApplicationConfiguration UID)
     */
    public String applicationId;

    @Override
    public int hashCode() {
        return Objects.hash(applicationId, groupId, host, instanceId);
    }

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
        ClickAndStartDescriptor other = (ClickAndStartDescriptor) obj;
        return Objects.equals(applicationId, other.applicationId) && Objects.equals(groupId, other.groupId)
                && Objects.equals(host, other.host) && Objects.equals(instanceId, other.instanceId);
    }
}
