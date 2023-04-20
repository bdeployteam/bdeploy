package io.bdeploy.minion.user;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
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
import io.bdeploy.ui.api.AuthGroupService;

public class UserGroupDatabase implements AuthGroupService {

    private static final Logger log = LoggerFactory.getLogger(UserGroupDatabase.class);

    public static final String NAMESPACE = "usergroups/";
    public static final String FILE_NAME = "usergroup.json";

    private final Cache<String, UserGroupInfo> userGroupCache = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(1_000).build();

    private final BHive target;

    public UserGroupDatabase(MinionRoot root) {
        this.target = root.getHive();
    }

    @Override
    public SortedSet<UserGroupInfo> getAll() {
        return getAllIds().stream().map(this::getUserGroup).collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public SortedSet<UserGroupInfo> getUserGroups(Set<String> ids) {
        return getAllIds().stream().filter(ids::contains).map(this::getUserGroup).collect(Collectors.toCollection(TreeSet::new));
    }

    private SortedSet<String> getAllIds() {
        Set<Key> keys = target.execute(new ManifestListOperation().setManifestName(NAMESPACE));
        return keys.stream().map(k -> k.getName().substring(NAMESPACE.length())).collect(Collectors.toCollection(TreeSet::new));
    }

    public UserGroupInfo getUserGroup(String id) {
        // Note: We are using getIfPresent and put instead of get(name, Callable) as we need to handle null values
        UserGroupInfo info = userGroupCache.getIfPresent(id);
        if (info != null) {
            return info;
        }
        Optional<Long> current = target.execute(new ManifestMaxIdOperation().setManifestName(NAMESPACE + id));
        if (!current.isPresent()) {
            return null;
        }

        Manifest.Key key = new Manifest.Key(NAMESPACE + id, String.valueOf(current.get()));
        Manifest mf = target.execute(new ManifestLoadOperation().setManifest(key));

        // check the manifest for manipulation to prevent from manually making somebody admin, etc.
        Set<ElementView> result = target.execute(new ObjectConsistencyCheckOperation().addRoot(key));
        if (!result.isEmpty()) {
            log.error("User group corruption detected for {}", id);
            return null;
        }
        try (InputStream is = target.execute(new TreeEntryLoadOperation().setRelativePath(FILE_NAME).setRootTree(mf.getRoot()))) {
            info = StorageHelper.fromStream(is, UserGroupInfo.class);
            userGroupCache.put(id, info);
            return info;
        } catch (Exception ex) {
            log.error("Failed to persist user group: {}", id, ex);
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
        internalUpdate(info);
    }

    @Override
    public synchronized void updatePermissions(String target, UserGroupPermissionUpdateDto[] permissions) {
        for (UserGroupPermissionUpdateDto dto : permissions) {
            UserGroupInfo info = getUserGroup(dto.group);
            if (info == null) {
                throw new IllegalStateException("Cannot find user group with id " + dto.group);
            }

            // clear all scoped permissions for 'group'
            info.permissions.removeIf(c -> target.equals(c.scope));

            // add given scoped permission
            if (dto.permission != null) {
                info.permissions.add(new ScopedPermission(target, dto.permission));
            }

            internalUpdate(info);
        }
    }

    private void internalUpdate(UserGroupInfo info) {
        validateUniqueName(info);
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
                    throw new IllegalStateException(String.format("Duplicate name {} with group {} ", g.name, g.id));
                });

    }

    @Override
    public void deleteUserGroup(String group) {
        Set<Key> mfs = target.execute(new ManifestListOperation().setManifestName(NAMESPACE + group));
        log.info("Deleting {} manifests for user group {}", mfs.size(), group);
        mfs.forEach(k -> target.execute(new ManifestDeleteOperation().setToDelete(k)));
        userGroupCache.invalidate(group);
    }

    @Override
    public UserInfo getCloneWithMergedPermissions(UserInfo info) {
        if (info == null) {
            return null;
        }

        Set<UserGroupInfo> groups = getUserGroups(info.groups);

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

}
