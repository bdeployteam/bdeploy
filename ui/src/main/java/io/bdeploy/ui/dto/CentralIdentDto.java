package io.bdeploy.ui.dto;

import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;

public class CentralIdentDto {

    /**
     * The instance group this identification is issued for
     */
    public InstanceGroupConfiguration config;
    /**
     * The logo of the instance group.
     */
    public byte[] logo;
    /**
     * The target server's attachment identification. This is the information as seen by the central server.
     * <p>
     * Name and description are informative only and may not match any known information on the local server, but auth <b>must</b>
     * be a valid token on the target server for the identification to be accepted.
     */
    public AttachIdentDto local;

}
