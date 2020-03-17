package io.bdeploy.minion.user.ldap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
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
import io.bdeploy.minion.user.Authenticator;

public class LdapAuthenticator implements Authenticator {

    private static final String LDAP_DN = "distinguishedName";

    private static final Logger log = LoggerFactory.getLogger(LdapAuthenticator.class);

    private static final String LDAP_SYSTEM = "LDAP";

    @Override
    public boolean isResponsible(UserInfo user, AuthenticationSettingsDto settings) {
        return user.external && LDAP_SYSTEM.equals(user.externalSystem);
    }

    @Override
    public UserInfo getInitialInfo(String username, char[] password, AuthenticationSettingsDto settings) {
        return findAuthenticateUpdate(new UserInfo(username), password, settings.ldapSettings);
    }

    @Override
    public UserInfo authenticate(UserInfo user, char[] password, AuthenticationSettingsDto settings) {
        if (!isResponsible(user, settings)) {
            return null;
        }

        Optional<LDAPSettingsDto> server = settings.ldapSettings.stream().filter(s -> s.id.equals(user.externalTag)).findAny();
        if (!server.isPresent()) {
            log.warn("LDAP server {} associated with user {} no longer available, will try all servers.", user.externalTag,
                    user.name);
        }

        return findAuthenticateUpdate(user, password, server.map(Collections::singletonList).orElse(settings.ldapSettings));
    }

    /**
     * Queries given servers for the given user, authenticates it and updates the given user with information from the server.
     *
     * @param user the user to check, will be updated with additional info on success.
     * @param password the password to check
     * @param servers the servers to query
     * @return the successfully authenticated user, or <code>null</code> if not successful.
     */
    private UserInfo findAuthenticateUpdate(UserInfo user, char[] password, List<LDAPSettingsDto> servers) {
        for (LDAPSettingsDto server : servers) {
            try {
                LdapContext ctx = createServerContext(server);
                try {
                    UserInfo found = performUserSearch(user, password, server, ctx);
                    if (found != null) {
                        return found;
                    }
                } finally {
                    closeServerContext(ctx);
                }
            } catch (NamingException e) {
                log.debug("Cannot authenticate {} on server {}", user.name, server.server);
                if (log.isTraceEnabled()) {
                    log.trace("Exception", e);
                }
            }
        }
        return null;
    }

    private UserInfo performUserSearch(UserInfo user, char[] password, LDAPSettingsDto server, LdapContext ctx)
            throws NamingException {
        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE); // TODO: make configurable
        sc.setReturningAttributes(getAttributesToFetch(server));

        String filter = "(&" + server.accountPattern + "(" + server.accountUserName + "={0}))";

        NamingEnumeration<SearchResult> res = ctx.search(server.accountBase, filter, new Object[] { user.name }, sc);

        for (SearchResult sr : toIterable(res.asIterator())) {
            try {
                return verifyAndUpdateSearchResult(user, password, server, ctx, sr);
            } catch (NamingException e) {
                log.warn("Cannot authenticate {} on server {}", user.name, server.server, e);
            }
        }
        return null;
    }

    private String[] getAttributesToFetch(LDAPSettingsDto server) {
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

    private UserInfo verifyAndUpdateSearchResult(UserInfo user, char[] password, LDAPSettingsDto server, LdapContext ctx,
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

    private LdapContext createServerContext(LDAPSettingsDto server) throws NamingException {
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

    private void closeServerContext(DirContext ctx) throws NamingException {
        ctx.close();
    }
}
