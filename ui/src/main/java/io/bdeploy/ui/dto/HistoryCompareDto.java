package io.bdeploy.ui.dto;

import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;

/** @deprecated no longer required with new UI, done on client */
@Deprecated
public class HistoryCompareDto {

    public InstanceConfigurationDto configA;
    public InstanceConfigurationDto configB;

}
