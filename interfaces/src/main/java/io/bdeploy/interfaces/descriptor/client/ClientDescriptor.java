package io.bdeploy.interfaces.descriptor.client;

import io.bdeploy.common.security.RemoteService;

/**
 * Describes a remote client to the launcher. This is the model of *.bdeploy files.
 * <p>
 * The file only contains definitely fixed information which is required to uniquely
 * identify the application on the hosting system. All other information might be
 * changed later by configuration and thus must be loaded every time (human readable
 * name, icon, ...).
 */
public class ClientDescriptor {

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
     * Client ID (ApplicationConfiguration UID)
     */
    public String clientId;

}
