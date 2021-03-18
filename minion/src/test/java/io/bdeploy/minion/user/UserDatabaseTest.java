package io.bdeploy.minion.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestMinion;

@ExtendWith(TestMinion.class)
public class UserDatabaseTest {

    @Test
    void userRoles(MinionRoot root) {
        UserDatabase db = root.getUsers();

        db.createLocalUser("JunitTest", "JunitTest", Collections.singletonList(ApiAccessToken.ADMIN_PERMISSION));

        UserInfo info = db.authenticate("JunitTest", "JunitTest");
        assertNotNull(info);
        assertNotNull(info.permissions);
        assertEquals(1, info.permissions.size());
        assertEquals(ApiAccessToken.ADMIN_PERMISSION.permission, info.permissions.iterator().next().permission);

        info.permissions.clear();
        info.fullName = "JunitTest User";
        info.email = "JunitTest.user@example.com";
        db.updateUserInfo(info);

        UserInfo updated = db.authenticate("JunitTest", "JunitTest");
        assertNotNull(updated);
        assertNotNull(updated.permissions);
        assertTrue(updated.permissions.isEmpty());

        assertEquals("JunitTest User", updated.fullName);
        assertEquals("JunitTest.user@example.com", updated.email);
    }

    @Test
    void userCleanup(MinionRoot root) {
        UserDatabase db = root.getUsers();

        db.createLocalUser("JunitTest", "JunitTest", null);
        for (int i = 0; i < 20; ++i) {
            UserInfo u = db.getUser("JunitTest");
            u.permissions.add(new ScopedPermission("Scope" + i, ScopedPermission.Permission.ADMIN));
            db.updateUserInfo(u);
        }

        BHive hive = root.getHive();
        assertEquals(10, hive.execute(new ManifestListOperation().setManifestName(UserDatabase.NAMESPACE + "junittest")).size());
    }

    @Test
    void crud(MinionRoot root) {
        UserDatabase db = root.getUsers();

        db.createLocalUser("JunitTest", "JunitTest", Collections.singleton(new ScopedPermission("JunitTest", Permission.WRITE)));
        db.updateLocalPassword("JunitTest", "newpw");

        UserInfo user = db.authenticate("JunitTest", "newpw");
        assertNotNull(user);

        user.fullName = "JunitTest User";
        user.email = "JunitTest@example.com";

        db.updateUserInfo(user);

        assertNotNull(db.authenticate("JunitTest", "newpw"));

        user = db.getUser(user.name);

        assertEquals("JunitTest User", user.fullName);
        assertEquals("JunitTest@example.com", user.email);

        db.deleteUser("JunitTest");

        assertNull(db.getUser("JunitTest"));
        assertNull(db.authenticate("JunitTest", "newpw"));
    }

    @Test
    public void userNames(MinionRoot root) {
        UserDatabase db = root.getUsers();
        int originalSize = db.getAllNames().size();

        ScopedPermission permission = new ScopedPermission("MyScope", Permission.ADMIN);
        db.createLocalUser("jUNit", "junit", Collections.singleton(permission));

        // Ensure it is stored in lower-case
        BHive hive = root.getHive();
        for (Key key : hive.execute(new ManifestListOperation().setManifestName(UserDatabase.NAMESPACE))) {
            String name = key.getName().substring(UserDatabase.NAMESPACE.length());
            assertTrue(StringHelper.isAllLowerCase(name));
        }

        // Attempt to create users with different case
        assertThrows(IllegalStateException.class, () -> db.createLocalUser("JUNIT", "JUNIT", Collections.emptyList()));
        assertThrows(IllegalStateException.class, () -> db.createLocalUser("Junit", "Junit", Collections.emptyList()));
        assertThrows(IllegalStateException.class, () -> db.createLocalUser("juniT", "juniT", Collections.emptyList()));

        // Attempt to authenticate with different case
        assertNotNull(db.authenticate("JUNIT", "junit"));

        // Try to get with different case
        assertNotNull(db.getUser("Junit"));

        // Query permissions
        assertTrue(db.isAuthorized("JUNit", permission));

        // Query all users - check if lowercase
        for (String name : db.getAllNames()) {
            assertTrue(StringHelper.isAllLowerCase(name));
        }
        for (UserInfo user : db.getAll()) {
            assertTrue(StringHelper.isAllLowerCase(user.name));
        }

        // Delete user
        db.deleteUser("JUNIT");
        assertNull(db.getUser("JUNIT"));
        assertEquals(originalSize, db.getAllNames().size());
    }

}
