package io.bdeploy.interfaces.settings;

import java.util.ArrayList;
import java.util.List;

public class AuthenticationSettingsDto {

    public boolean disableBasic;
    public List<LDAPSettingsDto> ldapSettings = new ArrayList<>();
    public OIDCSettingsDto oidcSettings = new OIDCSettingsDto();
    public Auth0SettingsDto auth0Settings = new Auth0SettingsDto();

}
