package io.bdeploy.minion.user.ldap;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.StringJoiner;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.settings.AuthenticationSettingsDto;
import io.bdeploy.interfaces.settings.LDAPSettingsDto;
import io.bdeploy.minion.user.AuthTrace;
import io.bdeploy.minion.user.Authenticator;

public class LdapAuthenticator implements Authenticator {

    private static final String LDAP_DN = "distinguishedName";

    private static final Logger log = LoggerFactory.getLogger(LdapAuthenticator.class);

    public static final String LDAP_SYSTEM = "LDAP";

    @Override
    public boolean isResponsible(UserInfo user, AuthenticationSettingsDto settings) {
        if (settings.ldapSettings.isEmpty()) {
            return false;
        }
        return user.external && LDAP_SYSTEM.equals(user.externalSystem);
    }

    @Override
    public boolean isAuthenticationValid(UserInfo user, AuthenticationSettingsDto settings) {
        return !user.inactive; // no better way right now, but in theory we need to check the LDAP server.
    }

    @Override
    public UserInfo getInitialInfo(String username, char[] password, AuthenticationSettingsDto settings, AuthTrace trace) {
        return findAuthenticateUpdate(new UserInfo(username), password, settings.ldapSettings, trace);
    }

    @Override
    public UserInfo authenticate(UserInfo user, char[] password, AuthenticationSettingsDto settings, AuthTrace trace) {
        if (!isResponsible(user, settings)) {
            return null;
        }

        Optional<LDAPSettingsDto> server = settings.ldapSettings.stream().filter(s -> s.id.equals(user.externalTag)).findAny();
        trace.log("User is associated to server " + (server.isPresent() ? server.get().server : user.externalTag));

        if (server.isPresent()) {
            UserInfo info = findAuthenticateUpdate(user, password, server.map(Collections::singletonList).get(), trace);

            if (info == null) {
                trace.log("Associated LDAP server " + user.externalTag + " can not authenticate " + user.name
                        + ", will try all servers");
            } else {
                return info;
            }
        } else {
            trace.log("LDAP server " + user.externalTag + " is no longer available, will try all servers");
            log.warn("LDAP server {} associated with user {} no longer available, will try all servers.", user.externalTag,
                    user.name);
        }

        return findAuthenticateUpdate(user, password, settings.ldapSettings, trace);
    }

    /**
     * Queries given servers for the given user, authenticates it and updates the given user with information from the server.
     *
     * @param user the user to check, will be updated with additional info on success.
     * @param password the password to check
     * @param servers the servers to query
     * @param trace collector for tracing information
     * @return the successfully authenticated user, or <code>null</code> if not successful.
     */
    private static UserInfo findAuthenticateUpdate(UserInfo user, char[] password, List<LDAPSettingsDto> servers,
            AuthTrace trace) {
        for (LDAPSettingsDto server : servers) {
            trace.log("  query server " + server.server);
            try {
                LdapContext ctx = null;
                try {
                    ctx = createServerContext(server);
                } catch (Exception e) {
                    log.error("Cannot create initial connection to {} as {}", server.server, server.user, e);
                    trace.log("    server " + server.server + ": connection failed");
                    continue;
                }

                try {
                    UserInfo found = performUserSearch(user, password, server, ctx, trace);
                    if (found != null) {
                        return found;
                    }
                } finally {
                    closeServerContext(ctx);
                }
            } catch (NamingException e) {
                trace.log("    server " + server.server + " failed: " + e.getMessage());
                log.debug("Cannot authenticate {} on server {}", user.name, server.server);
                if (log.isTraceEnabled()) {
                    log.trace("Exception", e);
                }
            }
        }
        return null;
    }

    private static UserInfo performUserSearch(UserInfo user, char[] password, LDAPSettingsDto server, LdapContext ctx,
            AuthTrace trace) throws NamingException {
        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE); // TODO: make configurable
        sc.setReturningAttributes(getAttributesToFetch(server));

        String filter = "(&" + server.accountPattern + "(" + server.accountUserName + "={0}))";

        trace.log("    filter = " + filter.replace("{0}", user.name));
        NamingEnumeration<SearchResult> res = ctx.search(server.accountBase, filter, new Object[] { user.name }, sc);

        for (SearchResult sr : toIterable(res.asIterator())) {
            try {
                trace.log("    found: " + sr.getAttributes().get(LDAP_DN).get(0));
                return verifyAndUpdateSearchResult(user, password, server, ctx, sr);
            } catch (NamingException e) {
                log.warn("Cannot authenticate {} on server {}", user.name, server.server, e);
            }
        }
        return null;
    }

    private static String[] getAttributesToFetch(LDAPSettingsDto server) {
        List<String> result = new ArrayList<>();

        result.add(LDAP_DN); // always need this
        result.add(server.accountUserName);

        if (server.accountEmail != null && !server.accountEmail.isBlank()) {
            result.add(server.accountEmail);
        }
        if (server.accountFullName != null && !server.accountFullName.isBlank()) {
            result.add(server.accountFullName);
        }

        return result.toArray(String[]::new);
    }

    private static UserInfo verifyAndUpdateSearchResult(UserInfo user, char[] password, LDAPSettingsDto server, LdapContext ctx,
            SearchResult sr) throws NamingException {
        String userDn = String.valueOf(sr.getAttributes().get(LDAP_DN).get(0));

        ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, userDn);
        ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, password);
        ctx.reconnect(null);

        user.external = true;
        user.externalSystem = LDAP_SYSTEM;
        user.externalTag = server.id;

        if (server.accountFullName != null && !server.accountFullName.isBlank()) {
            user.fullName = String.valueOf(sr.getAttributes().get(server.accountFullName).get(0));
        }
        if (server.accountEmail != null && !server.accountEmail.isBlank()) {
            Attribute attribute = sr.getAttributes().get(server.accountEmail);
            if (attribute != null) {
                user.email = String.valueOf(sr.getAttributes().get(server.accountEmail).get(0));
            }
        }
        return user;
    }

    private static <T> Iterable<T> toIterable(Iterator<T> iterator) {
        return () -> iterator;
    }

    private static LdapContext createServerContext(LDAPSettingsDto server) throws NamingException {
        Properties env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, server.server);
        env.put(Context.REFERRAL, server.followReferrals ? "follow" : "ignore");
        // TODO: test pooling: env.put("com.sun.jndi.ldap.connect.pool", "true");

        LdapContext ctx = new InitialLdapContext(env, null);
        ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, server.user);
        ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, server.pass);
        ctx.reconnect(null);
        return ctx;
    }

    private static void closeServerContext(DirContext ctx) throws NamingException {
        ctx.close();
    }

    public String testLdapServer(LDAPSettingsDto dto) {
        LdapContext ctx = null;
        String result = "OK";

        try {
            ctx = createServerContext(dto);

            try {
                // dummy search: lookup bind user, no need to process result
                SearchControls sc = new SearchControls();
                sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
                String filter = "(" + dto.accountUserName + "={0})";
                ctx.search(dto.accountBase, filter, new Object[] { dto.user }, sc);
            } catch (NameNotFoundException e) {
                result = exception2String(dto.server + ": base context not found: ", e);
            } catch (NamingException e) {
                result = exception2String(dto.server + ": query failed: ", e);
            } finally {
                try {
                    closeServerContext(ctx);
                } catch (Exception e) {
                    result = exception2String(dto.server + ": close failed: ", e);
                }
            }
        } catch (Exception e) {
            result = exception2String(dto.server + ": connection failed: ", e);
        }
        return result;
    }

    public List<LdapUserGroupInfo> importUserGroupsLdapServer(LDAPSettingsDto dto, StringJoiner feedback) {
        LdapContext ctx = null;
        feedback.add("Fetching User Groups...");
        List<LdapUserGroupInfo> groups = new ArrayList<>();

        try {
            ctx = createServerContext(dto);

            try {
                SearchControls sc = new SearchControls();
                sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
                // Fetch all groups
                NamingEnumeration<SearchResult> groupResults = ctx.search(dto.accountBase, dto.groupPattern, sc);
                while (groupResults.hasMore()) {
                    SearchResult searchResult = groupResults.next();
                    Attributes attributes = searchResult.getAttributes();
                    LdapUserGroupInfo groupInfo = new LdapUserGroupInfo(attributes, dto);
                    groups.add(new LdapUserGroupInfo(attributes, dto));
                    feedback.add("Fetched Group: " + groupInfo.name);
                }
                feedback.add("Fetched " + groups.size() + " groups");
            } catch (NameNotFoundException e) {
                feedback.add(exception2String(dto.server + ": base context not found: ", e));
            } catch (NamingException e) {
                feedback.add(exception2String(dto.server + ": query failed: ", e));
            } finally {
                try {
                    closeServerContext(ctx);
                } catch (Exception e) {
                    feedback.add(exception2String(dto.server + ": close failed: ", e));
                }
            }
        } catch (Exception e) {
            feedback.add(exception2String(dto.server + ": connection failed: ", e));
        }
        return groups;
    }

    public List<LdapUserInfo> importUsersLdapServer(LDAPSettingsDto dto, StringJoiner feedback) {
        LdapContext ctx = null;
        feedback.add("Fetching Users...");
        List<LdapUserInfo> users = new ArrayList<>();

        try {
            ctx = createServerContext(dto);

            try {
                SearchControls sc = new SearchControls();
                sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
                // fetch all users
                NamingEnumeration<SearchResult> userResults = ctx.search(dto.accountBase, dto.accountPattern, sc);
                while (userResults.hasMore()) {
                    SearchResult searchResult = userResults.next();
                    Attributes attributes = searchResult.getAttributes();
                    LdapUserInfo userInfo = new LdapUserInfo(attributes, dto);
                    users.add(userInfo);
                    feedback.add("Fetched User: " + userInfo.name);
                }
                feedback.add("Fetched " + users.size() + " users");
            } catch (NameNotFoundException e) {
                feedback.add(exception2String(dto.server + ": base context not found: ", e));
            } catch (NamingException e) {
                feedback.add(exception2String(dto.server + ": query failed: ", e));
            } finally {
                try {
                    closeServerContext(ctx);
                } catch (Exception e) {
                    feedback.add(exception2String(dto.server + ": close failed: ", e));
                }
            }
        } catch (Exception e) {
            feedback.add(exception2String(dto.server + ": connection failed: ", e));
        }
        return users;
    }

    private static String exception2String(String message, Exception exception) {
        try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
            exception.printStackTrace(pw);
            return message + '\n' + sw.toString();
        } catch (IOException ioe) {
            return "\nInternal Error: Can't close streams";
        }
    }
}
