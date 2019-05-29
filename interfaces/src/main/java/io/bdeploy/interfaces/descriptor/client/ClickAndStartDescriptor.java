package io.bdeploy.interfaces.descriptor.client;

import io.bdeploy.common.security.RemoteService;

/**
 * Describes the content of the Click&Start file.
 * <p>
 * The file only contains immutable data which is required to uniquely
 * identify the application on the hosting system. All other information might be
 * changed later by configuration and thus must be loaded every time (human readable
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

}
