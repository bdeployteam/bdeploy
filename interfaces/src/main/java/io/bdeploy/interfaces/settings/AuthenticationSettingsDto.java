package io.bdeploy.interfaces.settings;

import java.util.ArrayList;
import java.util.List;

public class AuthenticationSettingsDto {

    public boolean disableBasic;
    public List<LDAPSettingsDto> ldapSettings = new ArrayList<>();

}
