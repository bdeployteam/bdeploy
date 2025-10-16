package io.bdeploy.minion.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestMinion;

@ExtendWith(TestMinion.class)
class UserDatabaseTest {

    private static final String NAMESPACE = "users/";
    private static final Set<ScopedPermission> globalAdminSet = Collections.singleton(ScopedPermission.GLOBAL_ADMIN);

    @Test
    void testUserRoles(MinionRoot root) {
        UserDatabase db = root.getUsers();

        db.createLocalUser("JunitTest", "JunitTestJunitTest", Collections.singletonList(ScopedPermission.GLOBAL_ADMIN));

        UserInfo info = db.authenticate("JunitTest", "JunitTestJunitTest");
        assertNotNull(info);
        assertNotNull(info.permissions);
        assertEquals(1, info.permissions.size());
        assertEquals(ScopedPermission.GLOBAL_ADMIN.permission, info.permissions.iterator().next().permission);

        info.permissions.clear();
        info.fullName = "JunitTest User";
        info.email = "JunitTest.user@example.com";
        db.updateUserInfo(info);

        UserInfo updated = db.authenticate("JunitTest", "JunitTestJunitTest");
        assertNotNull(updated);
        assertNotNull(updated.permissions);
        assertTrue(updated.permissions.isEmpty());

        assertEquals("JunitTest User", updated.fullName);
        assertEquals("JunitTest.user@example.com", updated.email);
    }

    @Test
    void testUserCleanup(MinionRoot root) {
        UserDatabase db = root.getUsers();

        db.createLocalUser("JunitTest", "JunitTestJunitTest", null);
        for (int i = 0; i < 20; ++i) {
            UserInfo u = db.getUser("JunitTest");
            u.permissions.add(new ScopedPermission("Scope" + i, ScopedPermission.Permission.ADMIN));
            db.updateUserInfo(u);
        }
        assertEquals(10, root.getHive().execute(new ManifestListOperation().setManifestName(NAMESPACE + "junittest")).size());
    }

    @Test
    void testCrud(MinionRoot root) {
        UserDatabase db = root.getUsers();

        db.createLocalUser("JunitTest", "JunitTestJunitTest",
                Collections.singleton(new ScopedPermission("JunitTest", Permission.WRITE)));

        db.updateLocalPassword("JunitTest", "newpwnewpwnewpw");

        UserInfo user = db.authenticate("JunitTest", "newpwnewpwnewpw");
        assertNotNull(user);

        user.fullName = "JunitTest User";
        user.email = "JunitTest@example.com";

        db.updateUserInfo(user);
        assertNotNull(db.authenticate("JunitTest", "newpwnewpwnewpw"));

        user = db.getUser(user.name);
        assertEquals("JunitTest User", user.fullName);
        assertEquals("JunitTest@example.com", user.email);

        db.deleteUser("JunitTest");
        assertThrows(RuntimeException.class, () -> db.deleteUser("JunitTest"));
        assertNull(db.getUser("JunitTest"));
        assertNull(db.authenticate("JunitTest", "newpwnewpwnewpw"));
    }

    @Test
    void testUserNames(MinionRoot root) {
        UserDatabase db = root.getUsers();
        int originalSize = db.getAllNames().size();

        ScopedPermission permission = new ScopedPermission("MyScope", Permission.ADMIN);
        db.createLocalUser("jUNit", "junitjunitjunit", Collections.singleton(permission));

        // Ensure it is stored in lower-case
        BHive hive = root.getHive();
        for (Key key : hive.execute(new ManifestListOperation().setManifestName(NAMESPACE))) {
            String name = key.getName().substring(NAMESPACE.length());
            assertTrue(StringHelper.isAllLowerCase(name));
        }

        // Attempt to create users with different case
        assertThrows(RuntimeException.class, () -> db.createLocalUser("JUNIT", "JUNITJUNITJUNIT", Collections.emptyList()));
        assertThrows(RuntimeException.class, () -> db.createLocalUser("Junit", "JunitJunitJunit", Collections.emptyList()));
        assertThrows(RuntimeException.class, () -> db.createLocalUser("juniT", "juniTjuniTjuniT", Collections.emptyList()));

        // Attempt to authenticate with different case
        assertNotNull(db.authenticate("JUNIT", "junitjunitjunit"));

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

    @Test
    void testUserUpdate(MinionRoot root) {
        UserDatabase db = root.getUsers();

        UserInfo admin = db.getUser("test");

        String initialFullName = admin.fullName;
        String initialEmail = admin.email;

        admin.fullName = "new name";
        admin.email = "e@ma.il";

        assertNotEquals(initialFullName, admin.fullName);
        assertNotEquals(initialEmail, admin.email);

        UserInfo unchanged = db.getUser("test");
        assertNotEquals(admin.fullName, unchanged.fullName);
        assertNotEquals(admin.email, unchanged.email);
        db.updateUserInfo(admin);

        UserInfo changed = db.getUser("test");
        assertEquals(admin.fullName, changed.fullName);
        assertEquals(admin.email, changed.email);
    }

    @Test
    void testLastGlobalAdminDeletionPrevention(MinionRoot root) {
        UserDatabase db = root.getUsers();

        assertThrows(RuntimeException.class, () -> db.deleteUser("test")); // default global administrator for TestMinion

        db.createLocalUser("JunitTestAdmin1", "JunitTestJunitTest", globalAdminSet);
        db.deleteUser("JunitTestAdmin1");

        db.createLocalUser("JunitTestAdmin2", "JunitTestJunitTest", globalAdminSet);
        db.deleteUser("JunitTestAdmin2");
        assertThrows(RuntimeException.class, () -> db.deleteUser("test"));

        db.createLocalUser("JunitTestAdmin3", "JunitTestJunitTest", globalAdminSet);
        db.deleteUser("test");
        assertThrows(RuntimeException.class, () -> db.deleteUser("JunitTestAdmin3"));

        db.createLocalUser("JunitTestAdmin4", "JunitTestJunitTest", globalAdminSet);

        UserInfo user = db.getUser("JunitTestAdmin4");
        user.inactive = true;
        db.updateUserInfo(user);

        assertThrows(RuntimeException.class, () -> db.deleteUser("JunitTestAdmin3"));
        assertTrue(db.getUser("JunitTestAdmin3") != null);
        db.deleteUser("JunitTestAdmin4");
        assertTrue(db.getUser("JunitTestAdmin4") == null);
        assertThrows(RuntimeException.class, () -> db.deleteUser("JunitTestAdmin3"));
        assertTrue(db.getUser("JunitTestAdmin3") != null);
    }

    @Test
    void testLastGlobalAdminInactivationPrevention(MinionRoot root) {
        UserDatabase db = root.getUsers();

        UserInfo admin = db.getUser("test");
        admin.inactive = true;

        assertThrows(RuntimeException.class, () -> db.updateUserInfo(admin));
        assertFalse(db.getUser("test").inactive);

        db.createLocalUser("JunitTestAdmin1", "JunitTestJunitTest", globalAdminSet);
        db.updateUserInfo(admin);
        assertTrue(db.getUser("test").inactive);
    }

    @Test
    void testLastGlobalAdminDemotionPrevention(MinionRoot root) {
        UserDatabase db = root.getUsers();

        UserInfo admin = db.getUser("test");
        admin.permissions.clear();

        assertThrows(RuntimeException.class, () -> db.updateUserInfo(admin));
        assertTrue(db.getUser("test").permissions.contains(ScopedPermission.GLOBAL_ADMIN));

        db.createLocalUser("JunitTestAdmin1", "JunitTestJunitTest", globalAdminSet);
        db.updateUserInfo(admin);
        assertFalse(db.getUser("test").permissions.contains(ScopedPermission.GLOBAL_ADMIN));
    }

    @Test
    void testAllUsersGroupRemoval(MinionRoot root) {
        UserDatabase db = root.getUsers();

        UserInfo admin = db.getUser("test");

        // Check that removal from the all-users-group is impossible
        assertThrows(RuntimeException.class, () -> db.removeUserFromGroup(admin.name, UserGroupInfo.ALL_USERS_GROUP_ID));
    }
}
