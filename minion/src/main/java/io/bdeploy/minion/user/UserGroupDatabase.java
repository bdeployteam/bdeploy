package io.bdeploy.minion.user;

import static io.bdeploy.interfaces.UserGroupInfo.ALL_USERS_GROUP_ID;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
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
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserGroupPermissionUpdateDto;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.user.ldap.LdapUserGroupInfo;
import io.bdeploy.ui.api.AuthGroupService;

public class UserGroupDatabase implements AuthGroupService {

    private static final Logger log = LoggerFactory.getLogger(UserGroupDatabase.class);
    private static final String NAMESPACE = "usergroups/";
    private static final String FILE_NAME = "usergroup.json";

    private final Cache<String, UserGroupInfo> userGroupCache = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(1_000).build();

    private final BHive target;

    public UserGroupDatabase(MinionRoot root) {
        this.target = root.getHive();
    }

    public void addAllUsersGroup() {
        if (getUserGroup(ALL_USERS_GROUP_ID) != null) {
            return;
        }
        UserGroupInfo allUsersGroup = new UserGroupInfo();
        allUsersGroup.id = ALL_USERS_GROUP_ID;
        allUsersGroup.name = "all";
        allUsersGroup.description = "All Users Group";
        internalUpdate(allUsersGroup);
    }

    @Override
    public SortedSet<UserGroupInfo> getAll() {
        return getAllIds().stream().map(this::getUserGroup).filter(g -> g != null && g.name != null)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public SortedSet<UserGroupInfo> getUserGroups(Set<String> groupIds) {
        return getAllIds().stream().filter(groupIds::contains).map(this::getUserGroup).filter(g -> g != null && g.name != null)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private SortedSet<String> getAllIds() {
        Set<Key> keys = target.execute(new ManifestListOperation().setManifestName(NAMESPACE));
        return keys.stream().map(k -> k.getName().substring(NAMESPACE.length())).collect(Collectors.toCollection(TreeSet::new));
    }

    public UserGroupInfo getByName(String name) {
        return getAll().stream().filter(g -> name.equalsIgnoreCase(g.name)).findFirst().orElse(null);
    }

    @Override
    public UserGroupInfo getUserGroup(String groupId) {
        // Note: We are using getIfPresent and put instead of get(name, Callable) as we need to handle null values
        UserGroupInfo info = userGroupCache.getIfPresent(groupId);
        if (info != null) {
            return info;
        }

        Optional<Long> current = target.execute(new ManifestMaxIdOperation().setManifestName(NAMESPACE + groupId));
        if (!current.isPresent()) {
            return null;
        }

        // check the manifest for manipulation to prevent from manually making somebody admin, etc.
        Manifest.Key key = new Manifest.Key(NAMESPACE + groupId, String.valueOf(current.get()));
        Set<ElementView> result = target.execute(new ObjectConsistencyCheckOperation().addRoot(key));
        if (!result.isEmpty()) {
            log.error("User group corruption detected for {}", groupId);
            return null;
        }

        Manifest mf = target.execute(new ManifestLoadOperation().setManifest(key));
        try (InputStream is = target.execute(new TreeEntryLoadOperation().setRelativePath(FILE_NAME).setRootTree(mf.getRoot()))) {
            info = StorageHelper.fromStream(is, UserGroupInfo.class);
            userGroupCache.put(groupId, info);
            return info;
        } catch (Exception ex) {
            log.error("Failed to persist user group: {}", groupId, ex);
            return null;
        }
    }

    @Override
    public void createUserGroup(UserGroupInfo info) {
        info.id = UuidHelper.randomId();
        internalUpdate(info);
    }

    @Override
    public void updateUserGroup(UserGroupInfo info) {
        if (ALL_USERS_GROUP_ID.equals(info.id) && info.inactive) {
            throw new IllegalStateException("Cannot deactivate " + ALL_USERS_GROUP_ID);
        }
        internalUpdate(info);
    }

    @Override
    public synchronized void updatePermissions(String scope, UserGroupPermissionUpdateDto[] permissions) {
        for (UserGroupPermissionUpdateDto dto : permissions) {
            UserGroupInfo info = getUserGroup(dto.group);
            if (info == null) {
                throw new IllegalStateException("Cannot find user group with id " + dto.group);
            }

            // clear all scoped permissions for 'group'
            info.permissions.removeIf(c -> scope.equals(c.scope));

            // add given scoped permission
            if (dto.permission != null) {
                info.permissions.add(new ScopedPermission(scope, dto.permission));
            }

            internalUpdate(info);
        }
    }

    private void internalUpdate(UserGroupInfo info) {
        if (!ALL_USERS_GROUP_ID.equals(info.id)) {
            validateUniqueName(info);
        }
        try (Transaction t = target.getTransactions().begin()) {
            Long id = target.execute(new ManifestNextIdOperation().setManifestName(NAMESPACE + info.id));
            Manifest.Key key = new Manifest.Key(NAMESPACE + info.id, String.valueOf(id));

            Tree.Builder tree = new Tree.Builder();
            tree.add(new Tree.Key(FILE_NAME, Tree.EntryType.BLOB),
                    target.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(info))));

            target.execute(new InsertManifestOperation().addManifest(new Manifest.Builder(key)
                    .setRoot(target.execute(new InsertArtificialTreeOperation().setTree(tree))).build(null)));

            target.execute(new ManifestDeleteOldByIdOperation().setAmountToKeep(10).setToDelete(NAMESPACE + info.id));

            // update the cache.
            userGroupCache.put(info.id, info);
        }
    }

    private void validateUniqueName(UserGroupInfo info) {
        getAll().stream().filter(g -> !g.id.equalsIgnoreCase(info.id)) // exclude itself
                .filter(g -> g.name.equalsIgnoreCase(info.name)) // find group with duplicate name
                .findAny().ifPresent(g -> {
                    throw new IllegalStateException(String.format("Duplicate name %s for group %s", g.name, g.id));
                });
    }

    @Override
    public void deleteUserGroup(String groupId) {
        if (ALL_USERS_GROUP_ID.equals(groupId)) {
            throw new IllegalStateException("Cannot delete " + ALL_USERS_GROUP_ID);
        }
        Set<Key> mfs = target.execute(new ManifestListOperation().setManifestName(NAMESPACE + groupId));
        log.info("Deleting {} manifests for user group {}", mfs.size(), groupId);
        mfs.forEach(k -> target.execute(new ManifestDeleteOperation().setToDelete(k)));
        userGroupCache.invalidate(groupId);
    }

    @Override
    public UserInfo getCloneWithMergedPermissions(UserInfo info) {
        if (info == null) {
            return null;
        }

        Set<UserGroupInfo> groups = getUserGroups(info.getGroups());

        // calculate merged permissions
        Set<ScopedPermission> groupPermissions = groups.stream().filter(g -> !g.inactive).flatMap(g -> g.permissions.stream())
                .collect(Collectors.toSet());
        Set<ScopedPermission> mergedPermissions = new HashSet<>();
        mergedPermissions.addAll(info.permissions);
        mergedPermissions.addAll(groupPermissions);

        // dumb deep clone by JSON round-trip here - otherwise we might update the cached in memory object.
        UserInfo clone = StorageHelper.fromRawBytes(StorageHelper.toRawBytes(info), UserInfo.class);
        clone.mergedPermissions = mergedPermissions;

        return clone;
    }

    UserGroupInfo importLdapGroup(LdapUserGroupInfo group, StringJoiner feedback) {
        UserGroupInfo existing = getAll().stream().filter(g -> g.name.equalsIgnoreCase(group.name)).findAny().orElse(null);

        if (existing != null) {
            feedback.add("Group with name " + group.name + " already exists, updating.");
            existing.description = group.description;
            updateUserGroup(existing);
            return existing;
        }

        UserGroupInfo info = new UserGroupInfo();
        info.name = group.name;
        info.description = group.description;
        createUserGroup(info);
        feedback.add("Successfully imported group " + info.name + ". ID: " + info.id);
        return info;
    }
}
