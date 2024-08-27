package io.bdeploy.minion.user.ldap;

import java.util.HashSet;
import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.settings.LDAPSettingsDto;

/**
 * Holds information fetched from LDAP server necessary for import of users
 */
public class LdapUserInfo {

    public String dn;
    public String uid;
    public String name;
    public String fullname;
    public String email;

    public boolean hasMemberOfRef; // Active Directory
    public Set<String> memberOf = new HashSet<>();

    public UserInfo info;

    public LdapUserInfo(Attributes attributes, LDAPSettingsDto dto) throws NamingException {
        this.dn = getAttribute(attributes, "distinguishedName");
        this.uid = getAttribute(attributes, "uid");
        this.name = getAttribute(attributes, dto.accountUserName);
        this.fullname = getAttribute(attributes, dto.accountFullName);
        this.email = getAttribute(attributes, dto.accountEmail);
        processMembers(attributes);
    }

    private static String getAttribute(Attributes attributes, String key) throws NamingException {
        if (key == null || key.isBlank()) {
            return null;
        }
        var result = attributes.get(key);
        return result == null ? null : result.get().toString();
    }

    private void processMembers(Attributes attributes) throws NamingException {
        Attribute memberOfAttribute = attributes.get("memberOf");
        if (memberOfAttribute != null) {
            NamingEnumeration<?> memberOfValues = memberOfAttribute.getAll();
            while (memberOfValues.hasMore()) {
                String memberOfValue = (String) memberOfValues.next();
                if (memberOfValue != null && !memberOfValue.isBlank()) {
                    memberOf.add(memberOfValue);
                }
            }
            this.hasMemberOfRef = true;
        }
    }

}
