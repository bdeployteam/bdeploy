package io.bdeploy.minion.user;

import static io.bdeploy.interfaces.UserGroupInfo.ALL_USERS_GROUP_ID;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.ManifestDeleteOldByIdOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.ObjectConsistencyCheckOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.UserPermissionUpdateDto;
import io.bdeploy.interfaces.manifest.SettingsManifest;
import io.bdeploy.interfaces.settings.AuthenticationSettingsDto;
import io.bdeploy.interfaces.settings.LDAPSettingsDto;
import io.bdeploy.interfaces.settings.SpecialAuthenticators;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.user.ldap.LdapAuthenticator;
import io.bdeploy.minion.user.ldap.LdapUserGroupInfo;
import io.bdeploy.minion.user.ldap.LdapUserGroupInfo.MemberRefType;
import io.bdeploy.minion.user.ldap.LdapUserInfo;
import io.bdeploy.minion.user.oauth2.Auth0TokenAuthenticator;
import io.bdeploy.minion.user.oauth2.OktaTokenAuthenticator;
import io.bdeploy.minion.user.oauth2.OpenIDConnectAuthenticator;
import io.bdeploy.ui.api.AuthService;

/**
 * Persistence for users which are allowed to authenticate against this minion.
 * <p>
 * Intentionally kept simple (and potentially slow) for now.
 */
public class UserDatabase implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(UserDatabase.class);
    private static final Predicate<UserInfo> isGlobalAdmin = u -> u.permissions.stream()
            .anyMatch(p -> ScopedPermission.GLOBAL_ADMIN.equals(p));

    public static final String NAMESPACE = "users/";
    public static final String FILE_NAME = "user.json";

    private final Cache<String, UserInfo> userCache = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(1_000).build();

    private final MinionRoot root;
    private final BHive target;

    private final List<Authenticator> authenticators = new ArrayList<>();
    private final Map<SpecialAuthenticators, Authenticator> specialAuths = new EnumMap<>(SpecialAuthenticators.class);

    private final LdapAuthenticator ldapAuthenticator = new LdapAuthenticator();
    private final OpenIDConnectAuthenticator oidcAuthenticator = new OpenIDConnectAuthenticator();
    private final Auth0TokenAuthenticator auth0Authenticator = new Auth0TokenAuthenticator();
    private final OktaTokenAuthenticator oktaAuthenticator = new OktaTokenAuthenticator();
    private final UserGroupDatabase userGroupDatabase;

    public UserDatabase(MinionRoot root, UserGroupDatabase userGroupDatabase) {
        this.root = root;
        this.target = root.getHive();
        this.userGroupDatabase = userGroupDatabase;

        this.authenticators.add(new PasswordAuthentication());
        this.authenticators.add(oidcAuthenticator);
        this.authenticators.add(ldapAuthenticator);

        this.specialAuths.put(SpecialAuthenticators.AUTH0, auth0Authenticator);
        this.specialAuths.put(SpecialAuthenticators.OKTA, oktaAuthenticator);
    }

    @Override
    public void updateUserInfo(UserInfo info) {
        UserInfo old = getUser(info.name);

        if (old.external != info.external) {
            throw new UnsupportedOperationException("Update on external user not supported");
        }

        if (old.external) {
            // managed by external system.
            info.fullName = old.fullName;
            info.email = old.email;
        }

        info.password = old.password; // don't update this.

        internalUpdate(info.name, info);
    }

    @Override
    public synchronized void updatePermissions(String target, UserPermissionUpdateDto[] permissions) {
        for (UserPermissionUpdateDto dto : permissions) {
            UserInfo info = getUser(dto.user);
            if (info == null) {
                throw new IllegalStateException("Cannot find user " + dto.user);
            }

            // clear all scoped permissions for 'group'
            info.permissions.removeIf(c -> target.equals(c.scope));

            // add given scoped permission
            if (dto.permission != null) {
                info.permissions.add(new ScopedPermission(target, dto.permission));
            }

            internalUpdate(info.name, info);
        }
    }

    @Override
    public void removePermissions(String group) {
        SortedSet<UserInfo> allUsers = getAll();
        Set<UserPermissionUpdateDto> changedPermissions = new HashSet<>();
        for (UserInfo userInfo : allUsers) {
            if (userInfo.permissions.removeIf(c -> group.equals(c.scope))) {
                changedPermissions.add(new UserPermissionUpdateDto(userInfo.name, null));
            }
        }
        updatePermissions(group, changedPermissions.toArray(UserPermissionUpdateDto[]::new));
    }

    @Override
    public void createLocalUser(String user, String pw, Collection<ScopedPermission> permissions) {
        user = UserInfo.normalizeName(user);
        UserInfo info = getUser(user);
        if (info != null) {
            throw new IllegalStateException("User already exists: " + user);
        }

        info = new UserInfo(user);

        info.password = PasswordAuthentication.hash(pw.toCharArray());
        if (permissions != null) {
            info.permissions.addAll(permissions);
        }

        internalUpdate(user, info);
    }

    /**
     * Update the password of a local user.
     *
     * @param user the user name
     * @param pw the password to set or <code>null</code> to keep the current password
     */
    @Override
    public void updateLocalPassword(String user, String pw) {
        user = UserInfo.normalizeName(user);
        UserInfo info = getUser(user);
        if (info == null) {
            throw new IllegalStateException("Cannot find user " + user);
        }

        if (pw != null) {
            if (info.external) {
                throw new IllegalStateException("Cannot set password of externally-managed user");
            }
            info.password = PasswordAuthentication.hash(pw.toCharArray());
        }

        internalUpdate(user, info);
    }

    private synchronized void internalUpdate(String user, UserInfo info) {
        info.mergedPermissions = null;
        String normUser = UserInfo.normalizeName(user);

        try (Transaction t = target.getTransactions().begin()) {
            Long id = target.execute(new ManifestNextIdOperation().setManifestName(NAMESPACE + normUser));
            Manifest.Key key = new Manifest.Key(NAMESPACE + normUser, String.valueOf(id));

            Tree.Builder tree = new Tree.Builder();
            tree.add(new Tree.Key(FILE_NAME, Tree.EntryType.BLOB),
                    target.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(info))));

            target.execute(new InsertManifestOperation().addManifest(new Manifest.Builder(key)
                    .setRoot(target.execute(new InsertArtificialTreeOperation().setTree(tree))).build(null)));

            target.execute(new ManifestDeleteOldByIdOperation().setAmountToKeep(10).setToDelete(NAMESPACE + normUser));

            // update the cache.
            userCache.put(normUser, info);
        }
    }

    @Override
    public boolean deleteUser(String user) {
        user = UserInfo.normalizeName(user);
        UserInfo info = getUser(user);

        if (info == null) {
            // User does not exist - nothing to do
            return false;
        }

        // Abort if we are trying to delete the last active global administrator
        if (isGlobalAdmin.test(info) && getAll().stream().filter(u -> !u.equals(info)).filter(u -> !u.inactive)
                .filter(isGlobalAdmin).findAny().isEmpty()) {
            log.warn("Aborting deletion of user {} because no other active global admin could be found", user);
            return false;
        }

        Set<Key> mfs = target.execute(new ManifestListOperation().setManifestName(NAMESPACE + user));
        log.info("Deleting {} manifests for user {}", mfs.size(), user);
        mfs.forEach(k -> target.execute(new ManifestDeleteOperation().setToDelete(k)));
        userCache.invalidate(user);
        return true;
    }

    @Override
    public SortedSet<String> getAllNames() {
        Set<Key> keys = target.execute(new ManifestListOperation().setManifestName(NAMESPACE));
        return keys.stream().map(k -> k.getName().substring(NAMESPACE.length())).collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public SortedSet<UserInfo> getAll() {
        return getAllNames().stream().map(name -> {
            UserInfo cached = getUser(name);
            UserInfo clone = StorageHelper.fromRawBytes(StorageHelper.toRawBytes(cached), UserInfo.class);
            clone.password = null;
            return clone;
        }).collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public UserInfo authenticate(String user, String pw, SpecialAuthenticators... auths) {
        return authenticateInternal(user, pw, new AuthTrace(false), getAuthenticators(auths));
    }

    private List<Authenticator> getAuthenticators(SpecialAuthenticators... auths) {
        if (!hasNonNull(auths)) {
            return this.authenticators; // use all if not filtered
        }

        List<SpecialAuthenticators> alist = Arrays.asList(auths);
        return specialAuths.entrySet().stream().filter(e -> alist.contains(e.getKey())).map(Entry::getValue).toList();
    }

    private static <T> boolean hasNonNull(T[] ts) {
        if (ts == null || ts.length == 0) {
            return false;
        }

        for (T t : ts) {
            if (t != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> traceAuthentication(String user, String pw) {
        AuthTrace trace = new AuthTrace(true);
        authenticateInternal(user, pw, trace, authenticators);
        return trace.getMessages();
    }

    @Override
    public String testLdapServer(LDAPSettingsDto dto) {
        // if there's no password in the dto, check if it is an existing setting and take the password from there, if not, proceed with an empty password
        fillPassword(dto);
        return ldapAuthenticator.testLdapServer(dto);
    }

    @Override
    public String importAccountsLdapServer(LDAPSettingsDto dto) {

        StringJoiner feedback = new StringJoiner("\n");

        try {
            fillPassword(dto);

            var groups = ldapAuthenticator.importUserGroupsLdapServer(dto, feedback);
            for (LdapUserGroupInfo group : groups) {
                group.info = userGroupDatabase.importLdapGroup(group, feedback);
            }

            feedback.add("");

            var users = ldapAuthenticator.importUsersLdapServer(dto, feedback);

            for (LdapUserInfo user : users) {
                user.info = importLdapUser(dto, user, feedback);
            }
            feedback.add("");

            addUsersToGroups(users, groups, feedback);
        } catch (Exception e) {
            feedback.add(e.getMessage());
        }

        return feedback.toString();
    }

    private UserInfo importLdapUser(LDAPSettingsDto dto, LdapUserInfo user, StringJoiner feedback) {
        UserInfo existing = getAll().stream().filter(u -> u.name.equalsIgnoreCase(user.name)).findAny().orElse(null);

        if (existing != null && !existing.external) {
            feedback.add("Locally managed user " + user.name + " already exists, skipping info update for " + user.name);
            return existing;
        }

        if (existing != null) {
            feedback.add("User with name " + user.name + " already exists, updating.");
            existing.fullName = user.fullname;
            existing.email = user.email;
            existing.external = true;
            existing.externalSystem = LdapAuthenticator.LDAP_SYSTEM;
            existing.externalTag = dto.id;
            internalUpdate(existing.name, existing);
            return existing;
        }

        UserInfo info = new UserInfo(user.name);
        info.fullName = user.fullname;
        info.email = user.email;
        info.external = true;
        info.externalSystem = LdapAuthenticator.LDAP_SYSTEM;
        info.externalTag = dto.id;
        internalUpdate(info.name, info);
        feedback.add("Successfully imported user " + info.name);
        return info;
    }

    private void addUsersToGroups(List<LdapUserInfo> users, List<LdapUserGroupInfo> groups, StringJoiner feedback) {
        Map<UserInfo, Set<UserGroupInfo>> userToGroups = new HashMap<>();
        for (LdapUserInfo user : users) {
            Set<UserGroupInfo> groupSet = userToGroups.computeIfAbsent(user.info, i -> new HashSet<>());

            for (LdapUserGroupInfo group : groups) {
                if ((group.memberRef == MemberRefType.DN && group.members.contains(user.dn))
                        || (group.memberRef == MemberRefType.UID && group.members.contains(user.uid))) {
                    groupSet.add(group.info);
                }
            }
        }

        for (LdapUserGroupInfo group : groups) {
            for (LdapUserInfo user : users) {
                if (user.hasMemberOfRef && user.memberOf.contains(group.dn)) {
                    userToGroups.get(user.info).add(group.info);
                }
            }
        }

        for (Map.Entry<UserInfo, Set<UserGroupInfo>> e : userToGroups.entrySet()) {
            UserInfo user = e.getKey();
            for (UserGroupInfo group : e.getValue()) {
                addUserToLdapImportedGroup(user.name, group, feedback);
            }
        }
    }

    private void addUserToLdapImportedGroup(String username, UserGroupInfo info, StringJoiner feedback) {
        if (info != null) {
            addUserToGroup(info.id, username);
            feedback.add("Added user " + username + " to group " + info.name);
        }
    }

    // if there's no password in the dto, check if it is an existing setting and take the password from there, if not, proceed with an empty password
    private void fillPassword(LDAPSettingsDto dto) {
        if (dto.pass == null) {
            AuthenticationSettingsDto settings = SettingsManifest.read(target, root.getEncryptionKey(), false).auth;
            Optional<LDAPSettingsDto> storedDto = settings.ldapSettings.stream().filter(l -> l.id.equals(dto.id)).findFirst();
            if (storedDto.isPresent()) {
                dto.pass = storedDto.get().pass;
            }
        }
    }

    private UserInfo authenticateInternal(String user, String pw, AuthTrace trace, List<Authenticator> auths) {
        user = UserInfo.normalizeName(user);
        trace.log("normalized Username: \"" + user + "\"");
        AuthenticationSettingsDto settings = SettingsManifest.read(target, root.getEncryptionKey(), false).auth;

        UserInfo u = getUser(user);
        if (u == null) {
            trace.log("user unknown -> query all authenticators");
            for (Authenticator auth : auths) {
                UserInfo newU = internalInitialQuery(auth, null, user, pw, settings, trace);
                if (newU != null) {
                    return newU;
                }
            }
            trace.log("FAILURE");
            return null;
        }

        // find out if there is a responsible authenticator, and check it. in case this does not work
        for (Authenticator auth : auths) {
            boolean isResponsible = auth.isResponsible(u, settings);
            trace.log("Authenticator: " + auth.getClass().getSimpleName() + ", responsible: " + isResponsible);
            if (isResponsible) {
                UserInfo authenticated = auth.authenticate(u, pw.toCharArray(), settings, trace);
                if (authenticated != null) {
                    authenticated.lastActiveLogin = System.currentTimeMillis();
                    internalUpdate(user, authenticated);
                    logSuccess(trace, authenticated);
                    return authenticated;
                }
            }
        }

        // the associated authentication method did not work. to support "moving" users from one system to another,
        // we will treat this as if the user was a fresh one.
        trace.log("User known but unable to use existing authentication association. Trying all authenticators.");
        for (Authenticator auth : auths) {
            UserInfo newU = internalInitialQuery(auth, u, user, pw, settings, trace);
            if (newU != null) {
                return newU;
            }
        }

        trace.log("FAILURE");
        return null;
    }

    private UserInfo internalInitialQuery(Authenticator auth, UserInfo existing, String user, String pw,
            AuthenticationSettingsDto settings, AuthTrace trace) {
        trace.log("Authenticator: " + auth.getClass().getSimpleName());
        UserInfo newU = auth.getInitialInfo(user, pw.toCharArray(), settings, trace);
        if (newU != null) {
            if (existing != null) {
                // apply permissions from existing user to keep them intact.
                newU.permissions = existing.permissions;
                newU.inactive = existing.inactive;
                newU.setGroups(existing.getGroups());
            }

            newU.lastActiveLogin = System.currentTimeMillis();
            internalUpdate(user, newU);
            logSuccess(trace, newU);
            return newU;
        }

        return null;
    }

    public boolean isAuthenticationValid(UserInfo info) {
        AuthenticationSettingsDto settings = SettingsManifest.read(target, root.getEncryptionKey(), false).auth;
        for (Authenticator auth : authenticators) {
            if (auth.isResponsible(info, settings) && auth.isAuthenticationValid(info, settings)) {
                return true;
            }
        }
        for (Authenticator auth : specialAuths.values()) {
            if (auth.isResponsible(info, settings) && auth.isAuthenticationValid(info, settings)) {
                return true;
            }
        }
        return false;
    }

    private static void logSuccess(AuthTrace trace, UserInfo info) {
        trace.log("Retrieved User:");
        trace.log("  Name: " + info.name);
        trace.log("  Full Name: " + info.fullName);
        trace.log("  E-Mail: " + info.email);
        trace.log("  External: " + info.external);
        trace.log("  External System: " + info.externalSystem);
        trace.log("  External Tag: " + info.externalTag);

        trace.log("SUCCESS");
    }

    @Override
    public UserInfo getUser(String name) {
        name = UserInfo.normalizeName(name);
        // Note: We are using getIfPresent and put instead of get(name, Callable) as we need to handle null values
        UserInfo info = userCache.getIfPresent(name);
        if (info != null) {
            return info;
        }

        Optional<Long> current = target.execute(new ManifestMaxIdOperation().setManifestName(NAMESPACE + name));
        if (!current.isPresent()) {
            return null;
        }

        // check the manifest for manipulation to prevent from manually making somebody admin, etc.
        Manifest.Key key = new Manifest.Key(NAMESPACE + name, String.valueOf(current.get()));
        Set<ElementView> result = target.execute(new ObjectConsistencyCheckOperation().addRoot(key));
        if (!result.isEmpty()) {
            log.error("User corruption detected for {}", name);
            return null;
        }

        Manifest mf = target.execute(new ManifestLoadOperation().setManifest(key));
        try (InputStream is = target.execute(new TreeEntryLoadOperation().setRelativePath(FILE_NAME).setRootTree(mf.getRoot()))) {
            info = StorageHelper.fromStream(is, UserInfo.class);
            userCache.put(name, info);
            return info;
        } catch (Exception ex) {
            log.error("Failed to persist user: {}", name, ex);
            return null;
        }
    }

    @Override
    public void addUserToGroup(String groupId, String user) {
        UserInfo info = getUser(user);
        if (info == null) {
            throw new IllegalStateException("Cannot find user " + user);
        }
        info.getGroups().add(groupId);
        internalUpdate(info.name, info);
    }

    @Override
    public void removeUserFromGroup(String group, String user) {
        if (ALL_USERS_GROUP_ID.equals(group)) {
            throw new IllegalStateException("Cannot remove user " + user + " from " + ALL_USERS_GROUP_ID);
        }
        UserInfo info = getUser(user);
        if (info == null) {
            throw new IllegalStateException("Cannot find user " + user);
        }
        info.getGroups().remove(group);
        internalUpdate(info.name, info);
    }

    @Override
    public boolean isAuthorized(String user, ScopedPermission required) {
        user = UserInfo.normalizeName(user);
        UserInfo info = getUser(user);
        if (info == null) {
            return false;
        }
        UserInfo cloned = userGroupDatabase.getCloneWithMergedPermissions(info);
        for (ScopedPermission permission : cloned.mergedPermissions) {
            if (permission.satisfies(required)) {
                return true;
            }
        }
        return false;
    }
}
