package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.AuthAdminResource;
import io.bdeploy.ui.api.AuthResource;

@ExtendWith(TestMinion.class)
class AuthAdminResourceTest {

    @Test
    void testDeletionOfLastAdminUser(AuthResource auth) {
        AuthAdminResource admin = auth.getAdmin();

        // Check that the last global administrator cannot be deleted
        String initialAdminName = admin.getAllUser().iterator().next().name;
        assertEquals(1, admin.getAllUser().size());
        assertFalse(admin.deleteUser(initialAdminName));
        assertEquals(1, admin.getAllUser().size());

        // Check that the last active global administrator cannot be deleted
        UserInfo inactiveAdmin = new UserInfo("inactiveadmin");
        inactiveAdmin.password = "pwpwpwpwpwpwpwpwpwpwpw";
        inactiveAdmin.inactive = true;
        inactiveAdmin.permissions = Set.of(ScopedPermission.GLOBAL_ADMIN);
        admin.createLocalUser(inactiveAdmin);

        assertEquals(2, admin.getAllUser().size());
        assertFalse(admin.deleteUser(initialAdminName));
        assertEquals(2, admin.getAllUser().size());

        inactiveAdmin.inactive = false;
        admin.updateUser(inactiveAdmin);

        assertTrue(admin.deleteUser(initialAdminName));
        assertEquals(1, admin.getAllUser().size());
    }

    @Test
    void testCreateUpdateDeleteUser(AuthResource auth) {
        AuthAdminResource admin = auth.getAdmin();

        ScopedPermission globalReadPermission = new ScopedPermission(ScopedPermission.Permission.READ);

        UserInfo myUser = new UserInfo("Ash Ketschup");
        myUser.password = "pikachu_is_too_short";
        myUser.fullName = "Ashophy P. K. M. N. Ketschup the First";
        myUser.email = "some@pk.mn";

        UserInfo fetchedUser;

        // Ensure baseline
        assertEquals(1, admin.getAllUser().size()); // Default global administrator user already exists
        assertEquals(1, admin.getAllUserNames().size()); // Default global administrator user already exists

        // Test user creation
        admin.createLocalUser(myUser);
        assertEquals(2, admin.getAllUser().size());
        assertEquals(2, admin.getAllUserNames().size());

        // Check user creation
        fetchedUser = admin.getUser("Ash Ketschup");
        assertEquals("Ashophy P. K. M. N. Ketschup the First", fetchedUser.fullName);
        assertEquals("some@pk.mn", fetchedUser.email);
        assertTrue(fetchedUser.permissions.isEmpty());

        // Test data modification
        myUser.fullName = "Dialga > Palika";
        myUser.email = "the@truth.forsure";
        admin.updateUser(myUser);

        // Check data modification
        fetchedUser = admin.getUser("Ash Ketschup");
        assertEquals("Dialga > Palika", fetchedUser.fullName);
        assertEquals("the@truth.forsure", fetchedUser.email);
        assertTrue(fetchedUser.permissions.isEmpty());

        // Test permission addition
        myUser.permissions.add(globalReadPermission);
        admin.updateUser(myUser);

        // Check permission addition
        fetchedUser = admin.getUser("Ash Ketschup");
        assertEquals(1, fetchedUser.permissions.size());
        assertEquals(globalReadPermission, fetchedUser.permissions.iterator().next());

        // Test permission deletion
        myUser.permissions.remove(globalReadPermission);
        admin.updateUser(myUser);

        // Check permission deletion
        fetchedUser = admin.getUser("Ash Ketschup");
        assertTrue(fetchedUser.permissions.isEmpty());

        // Test user deletion
        admin.deleteUser("Ash Ketschup");
        assertEquals(1, admin.getAllUser().size());
        assertEquals(1, admin.getAllUserNames().size());

        // Check user deletion
        fetchedUser = admin.getUser("Ash Ketschup");
        assertNull(fetchedUser);
    }

    @Test
    void testUserGroups(AuthResource auth) {
        AuthAdminResource admin = auth.getAdmin();

        Function<Collection<UserGroupInfo>, UserGroupInfo> groupGetter = c -> c.stream().filter(g -> g.name.equals("TestGroup"))
                .findAny().get();

        UserGroupInfo userGroupInfo = new UserGroupInfo();
        userGroupInfo.name = "TestGroup";
        userGroupInfo.description = "Cool Test Group";
        userGroupInfo.permissions = Set.of(new ScopedPermission(ScopedPermission.Permission.READ));

        SortedSet<UserGroupInfo> allUserGroups;
        UserGroupInfo myGroup;

        allUserGroups = admin.getAllUserGroups();
        assertEquals(1, allUserGroups.size()); // all-users-group always exists

        admin.createUserGroup(userGroupInfo);
        allUserGroups = admin.getAllUserGroups();
        assertEquals(2, allUserGroups.size());

        myGroup = groupGetter.apply(allUserGroups);
        assertEquals("TestGroup", myGroup.name);
        assertEquals("Cool Test Group", myGroup.description);
        assertEquals(userGroupInfo.permissions, myGroup.permissions);

        myGroup.description = "New description";

        admin.updateUserGroup(myGroup);
        allUserGroups = admin.getAllUserGroups();
        assertEquals(2, allUserGroups.size());

        myGroup = groupGetter.apply(allUserGroups);
        assertEquals("TestGroup", myGroup.name);
        assertEquals("New description", myGroup.description);
        assertEquals(userGroupInfo.permissions, myGroup.permissions);
    }
}
