package io.bdeploy.launcher.cli;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.Version;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;

/**
 * Additional information stored along with the client software configuration so that we can display this information to the user
 * without doing any remote calls. The information stored in here represents the last state when the application was launched and
 * is only updated on-demand.
 */
public class ClientApplicationDto {

    /**
     * Globally unique identifier of the application
     */
    public String id;

    /**
     * The human readable name of the application
     */
    public String appName;

    /**
     * The human readable name of the instance
     */
    public String instanceName;

    /**
     * The human readable name of the instance group
     */
    public String instanceGroupTitle;

    /**
     * The purpose of the instance
     */
    public InstancePurpose purpose;

    /**
     * The product version that the remote server had during the last sync operation
     */
    public Manifest.Key product;

    /**
     * Whether the application supports autostart
     */
    public boolean supportsAutostart;

    /**
     * The version of the server that this client application stems from.
     */
    public String serverVersion;

    /**
     * Whether the server wants the application to autostart on system boot
     */
    public boolean autostart;

    /**
     * The name of the start script of the client application
     */
    public String startScriptName;

    /**
     * The file extension that the file association will be bound to
     */
    public String fileAssocExtension;

    /**
     * Whether the application may be launched without contacting the BDeploy minion first
     */
    public boolean offlineStartAllowed;

    /**
     * Creates a new instance using the given configuration
     */
    public static ClientApplicationDto create(ClickAndStartDescriptor desc, ClientApplicationConfiguration cfg,
            Version serverVersion) {
        ClientApplicationDto dto = new ClientApplicationDto();

        dto.instanceGroupTitle = cfg.instanceGroupTitle;
        if (dto.instanceGroupTitle == null) {
            // Older versions do not provide a title so we fallback to the group
            dto.instanceGroupTitle = desc.groupId;
        }

        InstanceNodeConfiguration instanceConfig = cfg.instanceConfig;
        dto.instanceName = instanceConfig.name;
        dto.purpose = instanceConfig.purpose;
        dto.product = instanceConfig.product;

        ProcessControlDescriptor processControlDescr = cfg.appDesc.processControl;
        dto.supportsAutostart = processControlDescr.supportsAutostart;
        dto.startScriptName = processControlDescr.startScriptName;
        dto.fileAssocExtension = processControlDescr.fileAssocExtension;
        dto.offlineStartAllowed = processControlDescr.offlineStartAllowed;

        ApplicationConfiguration appConfig = cfg.appConfig;
        dto.id = appConfig.id;
        dto.appName = appConfig.name;
        dto.autostart = appConfig.processControl.autostart;

        dto.serverVersion = serverVersion.toString();

        return dto;
    }
}
