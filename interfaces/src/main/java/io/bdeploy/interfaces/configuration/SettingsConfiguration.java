package io.bdeploy.interfaces.configuration;

import io.bdeploy.interfaces.settings.AuthenticationSettingsDto;
import io.bdeploy.interfaces.settings.GeneralSettingsDto;
import io.bdeploy.interfaces.settings.InstanceGroupSettingsDto;

/**
 * All settings for the minion which can be configured by the user.
 */
public class SettingsConfiguration {

    public GeneralSettingsDto general = new GeneralSettingsDto();

    public AuthenticationSettingsDto auth = new AuthenticationSettingsDto();

    public InstanceGroupSettingsDto instanceGroup = new InstanceGroupSettingsDto();

}
