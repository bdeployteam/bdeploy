package io.bdeploy.minion.user.ldap;

import java.util.HashSet;
import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.settings.LDAPSettingsDto;

/**
 * Holds information fetched from LDAP server necessary for import of user groups
 */
public class LdapUserGroupInfo {

    public enum MemberRefType {
        DN, // Active Directory
        UID // OpenLDAP
    }

    public String dn;
    public String name;
    public String description;

    public MemberRefType memberRef;
    public Set<String> members = new HashSet<>();

    public UserGroupInfo info;

    public LdapUserGroupInfo(Attributes attributes, LDAPSettingsDto dto) throws NamingException {
        this.dn = getAttribute(attributes, "distinguishedName");
        this.name = getAttribute(attributes, dto.groupName);
        this.description = getAttribute(attributes, dto.groupDescription);
        processMembers(attributes);
    }

    private String getAttribute(Attributes attributes, String key) throws NamingException {
        if (key == null || key.isBlank()) {
            return null;
        }
        var result = attributes.get(key);
        return result == null ? null : result.get().toString();
    }

    private void processMembers(Attributes attributes) throws NamingException {
        if (attributes.get("member") != null) {
            Attribute memberAttribute = attributes.get("member");
            NamingEnumeration<?> memberValues = memberAttribute.getAll();
            while (memberValues.hasMore()) {
                String memberValue = (String) memberValues.next();
                if (memberValue != null && !memberValue.isBlank()) {
                    members.add(memberValue);
                }
            }
            this.memberRef = MemberRefType.DN;
        } else if (attributes.get("memberuid") != null) {
            Attribute memberAttribute = attributes.get("memberuid");
            NamingEnumeration<?> memberValues = memberAttribute.getAll();
            while (memberValues.hasMore()) {
                String memberValue = (String) memberValues.next();
                if (memberValue != null && !memberValue.isBlank()) {
                    members.add(memberValue);
                }
            }
            this.memberRef = MemberRefType.UID;
        }
    }

}
