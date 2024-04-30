package io.bdeploy.launcher.cli;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;

/**
 * Additional information stored along with the client software configuration so that we can display this information to the user
 * without doing any remote calls. The information stored in here represents the last state when the application was launched and
 * is only updated on-demand.
 */
public class ClientApplicationDto {

    /**
     * Creates a new instance using the given configuration
     */
    public static ClientApplicationDto create(ClickAndStartDescriptor desc, ClientApplicationConfiguration cfg) {
        ClientApplicationDto dto = new ClientApplicationDto();
        dto.appName = cfg.appConfig.name;
        dto.instanceName = cfg.instanceConfig.name;
        dto.purpose = cfg.instanceConfig.purpose;
        dto.product = cfg.instanceConfig.product;
        dto.supportsAutostart = cfg.appDesc.processControl.supportsAutostart;
        dto.autostart = cfg.appConfig.processControl.autostart;

        // Older versions do not provide a title so we fallback to the group
        dto.instanceGroupTitle = cfg.instanceGroupTitle;
        if (dto.instanceGroupTitle == null) {
            dto.instanceGroupTitle = desc.groupId;
        }
        return dto;
    }

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
     * The product version
     */
    public Manifest.Key product;

    /**
     * Whether the application supports autostart
     */
    public boolean supportsAutostart;

    /**
     * Whether the server wants the application to autostart on system boot
     */
    public boolean autostart;
}
