package io.bdeploy.minion.user;

import static io.bdeploy.interfaces.UserGroupInfo.ALL_USERS_GROUP_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserGroupPermissionUpdateDto;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestMinion;

@ExtendWith(TestMinion.class)
class UserGroupDatabaseTest {

    @Test
    void testGroupScopedPermission(MinionRoot root) {
        UserDatabase users = root.getUsers();
        UserGroupDatabase groups = root.getUserGroups();
        String userName = "JunitTest";
        String scope = "TestInstanceGroup";
        ScopedPermission permission = new ScopedPermission(scope, Permission.ADMIN);

        users.createLocalUser(userName, "JunitTestJunitTest", Collections.emptyList());

        UserGroupInfo g = new UserGroupInfo();
        g.name = "TestGroup";
        assertNull(g.id); // group is not created yet
        groups.createUserGroup(g);
        assertNotNull(g.id); // group successfully created

        UserGroupPermissionUpdateDto groupPermissionUpdateDto = new UserGroupPermissionUpdateDto(g.id, permission.permission);
        groups.updatePermissions(scope, new UserGroupPermissionUpdateDto[] { groupPermissionUpdateDto });

        g = groups.getUserGroup(g.id); // fetch group again to refresh permissions

        // group has the permission
        assertEquals(1, g.permissions.size());
        assertEquals(permission.scope, g.permissions.stream().toList().get(0).scope);
        assertEquals(permission.permission, g.permissions.stream().toList().get(0).permission);

        // test user does not have a permission
        assertFalse(users.isAuthorized(userName, permission));

        // test user is added to group with needed permission
        users.addUserToGroup(g.id, userName);

        // test user does have a required permission
        assertTrue(users.isAuthorized(userName, permission));

        // remove user from group
        users.removeUserFromGroup(g.id, userName);

        // test user no longer has the permission
        assertFalse(users.isAuthorized(userName, permission));

        // ensure that removing the user from a group that they are not in doesn't break
        users.removeUserFromGroup(g.id, userName);
    }

    @Test
    void testGroupGlobalPermission(MinionRoot root) {
        UserDatabase users = root.getUsers();
        UserGroupDatabase groups = root.getUserGroups();
        String userName = "JunitTest";
        ScopedPermission permission = new ScopedPermission(null, Permission.ADMIN);

        users.createLocalUser(userName, "JunitTestJunitTest", Collections.emptyList());

        UserGroupInfo g = new UserGroupInfo();
        g.name = "TestGroup";
        groups.createUserGroup(g);
        UserGroupPermissionUpdateDto groupPermissionUpdateDto = new UserGroupPermissionUpdateDto(g.id, permission.permission);
        groups.updatePermissions(null, new UserGroupPermissionUpdateDto[] { groupPermissionUpdateDto });

        UserInfo info = users.getUser(userName);
        // user info itself does not hold merged permissions
        assertNull(info.mergedPermissions);
        // merged permissions are empty
        assertTrue(groups.getCloneWithMergedPermissions(info).mergedPermissions.isEmpty());

        users.addUserToGroup(g.id, userName);

        // user info itself does not hold merged permissions
        assertNull(info.mergedPermissions);
        // merged permissions have global permission from group
        assertTrue(groups.getCloneWithMergedPermissions(info).mergedPermissions.contains(permission));

        // remove user from group
        users.removeUserFromGroup(g.id, userName);

        // ensure that removing the user from a group that they are not in doesn't break
        users.removeUserFromGroup(g.id, userName);
    }

    @Test
    void testAllUsersGroupIsPresentByDefault(MinionRoot root) {
        UserDatabase users = root.getUsers();
        UserGroupDatabase groups = root.getUserGroups();
        String userName = "JunitTest";
        ScopedPermission permission = new ScopedPermission(null, Permission.ADMIN);

        users.createLocalUser(userName, "JunitTestJunitTest", Collections.emptyList());

        UserGroupPermissionUpdateDto groupPermissionUpdateDto = new UserGroupPermissionUpdateDto(ALL_USERS_GROUP_ID,
                permission.permission);
        groups.updatePermissions(null, new UserGroupPermissionUpdateDto[] { groupPermissionUpdateDto });

        UserInfo info = users.getUser(userName);

        // user info itself does not hold merged permissions
        assertNull(info.mergedPermissions);
        // user contains all users group by default
        assertTrue(info.getGroups().contains(ALL_USERS_GROUP_ID));
        // merged permissions have global permission from all users group
        assertTrue(groups.getCloneWithMergedPermissions(info).mergedPermissions.contains(permission));
    }
}
