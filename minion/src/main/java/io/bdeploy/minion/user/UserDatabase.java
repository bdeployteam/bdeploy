package io.bdeploy.minion.user;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
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
import io.bdeploy.common.security.ApiAccessToken.ScopedCapability;
import io.bdeploy.ui.api.AuthService;

/**
 * Persistence for users which are allowed to authenticate against this minion.
 * <p>
 * Intentionally kept simple (and potentially slow) for now.
 */
public class UserDatabase implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(UserDatabase.class);
    private static final String NAMESPACE = "users/";
    private static final String FILE_NAME = "user.json";

    private final PasswordAuthentication auth = new PasswordAuthentication();
    private final BHive target;

    public UserDatabase(BHive target) {
        this.target = target;
    }

    /**
     * Add or update an existing user
     *
     * @param user the user name
     * @param pw the password to set or <code>null</code> to keep the current password
     * @param capabilities the capabilities to set or <code>null</code> to keep the current ones
     */
    public void updateUser(String user, String pw, List<ScopedCapability> capabilities) {
        UserInfo info = findUser(user);
        if (info == null) {
            info = new UserInfo(user);
        }

        if (pw != null) {
            info.password = auth.hash(pw.toCharArray());
        }
        if (capabilities != null) {
            info.capabilities = capabilities;
        }

        internalUpdate(user, info);
    }

    /**
     * @param user the user to update
     * @param recently the list of recently used items (instance groups)
     */
    public void updateRecentlyUsed(String user, List<String> recently) {
        UserInfo info = findUser(user);
        if (info == null) {
            throw new IllegalStateException("Cannot find user " + user);
        }

        info.recentlyUsedInstanceGroups = recently;
        internalUpdate(user, info);
    }

    @Override
    public List<String> getRecentlyUsedInstanceGroups(String user) {
        UserInfo info = findUser(user);
        if (info == null) {
            return Collections.emptyList();
        }

        return info.recentlyUsedInstanceGroups;
    }

    @Override
    public void addRecentlyUsedInstanceGroup(String user, String group) {
        UserInfo info = findUser(user);
        if (info == null) {
            return;
        }
        List<String> recentlyUsed = info.recentlyUsedInstanceGroups;

        // Check if the current group is already the latest one
        int index = recentlyUsed.indexOf(group);
        if (index != -1 && index == recentlyUsed.size() - 1) {
            return;
        }

        // force group to be last in the list.
        recentlyUsed.remove(group);
        while (recentlyUsed.size() >= 10) {
            recentlyUsed.remove(0);
        }

        recentlyUsed.add(group);
        internalUpdate(user, info);
    }

    private synchronized void internalUpdate(String user, UserInfo info) {
        Long id = target.execute(new ManifestNextIdOperation().setManifestName(NAMESPACE + user));
        Manifest.Key key = new Manifest.Key(NAMESPACE + user, String.valueOf(id));

        Tree.Builder root = new Tree.Builder();
        root.add(new Tree.Key(FILE_NAME, Tree.EntryType.BLOB),
                target.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(info))));

        target.execute(new InsertManifestOperation().addManifest(new Manifest.Builder(key)
                .setRoot(target.execute(new InsertArtificialTreeOperation().setTree(root))).build(null)));

        target.execute(new ManifestDeleteOldByIdOperation().setAmountToKeep(10).setToDelete(NAMESPACE + user));
    }

    public void removeUser(String user) {
        SortedSet<Key> mfs = target.execute(new ManifestListOperation().setManifestName(NAMESPACE + user));
        log.info("Deleting {} manifests for user {}", mfs.size(), user);
        mfs.forEach(k -> target.execute(new ManifestDeleteOperation().setToDelete(k)));
    }

    public Set<String> getAllNames() {
        SortedSet<Key> keys = target.execute(new ManifestListOperation().setManifestName(NAMESPACE));
        return keys.stream().map(k -> k.getName().substring(NAMESPACE.length())).collect(Collectors.toSet());
    }

    @Override
    public UserInfo authenticate(String user, String pw) {
        UserInfo u = findUser(user);
        if (u == null) {
            return null;
        }
        if (auth.authenticate(pw.toCharArray(), u.password)) {
            return u;
        }
        return null;
    }

    private UserInfo findUser(String name) {
        Optional<Long> current = target.execute(new ManifestMaxIdOperation().setManifestName(NAMESPACE + name));
        if (!current.isPresent()) {
            return null;
        }

        Manifest.Key key = new Manifest.Key(NAMESPACE + name, String.valueOf(current.get()));
        Manifest mf = target.execute(new ManifestLoadOperation().setManifest(key));

        // check the manifest for manipulation to prevent from manually making somebody admin, etc.
        Set<ElementView> result = target.execute(new ObjectConsistencyCheckOperation().addRoot(key));
        if (!result.isEmpty()) {
            log.error("User corruption detected for {}", name);
            return null;
        }

        try (InputStream is = target.execute(new TreeEntryLoadOperation().setRelativePath(FILE_NAME).setRootTree(mf.getRoot()))) {
            return StorageHelper.fromStream(is, UserInfo.class);
        } catch (IOException e) {
            log.error("Cannot load user: {}", name, e);
            return null;
        }
    }

}
