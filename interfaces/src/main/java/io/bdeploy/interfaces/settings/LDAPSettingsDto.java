package io.bdeploy.interfaces.settings;

public class LDAPSettingsDto {

    /**
     * The URL of the LDAP server.
     */
    public String server;

    /**
     * Description of the server
     */
    public String description;

    /**
     * The user to use to bind to the LDAP server.
     */
    public String user;

    /**
     * The password for the LDAP server
     */
    public String pass;

    /**
     * The base to search in (DC, ...)
     */
    public String accountBase;

    /**
     * The query which yields all accounts on the server.
     * <p>
     * The {@link #accountUserName} field is appended to the filter to filter for the currently searched user.
     */
    public String accountPattern;

    /**
     * The name of the field in the response which contains the users username
     */
    public String accountUserName;

    /**
     * The name of the field in the response which contains the users full name
     */
    public String accountFullName;

    /**
     * The name of the field in the response which contains the users E-Mail address
     */
    public String accountEmail;

    /**
     * Whether to follow referrals in LDAP query results.
     */
    public boolean followReferrals;

}
