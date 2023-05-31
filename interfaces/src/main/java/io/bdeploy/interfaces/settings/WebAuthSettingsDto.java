package io.bdeploy.interfaces.settings;

/**
 * Settings related to authentication mechanisms using a web based flow.
 */
public class WebAuthSettingsDto {

    public Auth0SettingsDto auth0;
    public OktaSettingsDto okta;
}
