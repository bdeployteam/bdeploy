package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.GeneralSecurityException;
import java.util.Set;
import java.util.SortedSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.api.remote.v1.dto.CredentialsApi;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.AuthAdminResource;
import io.bdeploy.ui.api.AuthResource;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Response;

@ExtendWith(TestMinion.class)
class AuthResourceTest {

    @Test
    void testAuth(AuthResource auth, TestMinion backend) throws GeneralSecurityException {
        Response notAuth = auth.authenticate(new CredentialsApi("some", "value"));
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), notAuth.getStatus());

        Response resp = auth.authenticate(new CredentialsApi("Test", "TheTestPassword"));
        String token = resp.readEntity(String.class);
        ApiAccessToken decoded = SecurityHelper.getInstance().getVerifiedPayload(token, ApiAccessToken.class,
                backend.getKeyStore());

        assertNotNull(decoded);
        assertEquals("Test", decoded.getIssuedTo());
    }

    @Test
    void testCurrentUserDataUpdateLogic(AuthResource auth) {
        UserInfo userInfo = auth.getCurrentUser();
        UserInfo newUserInfo = new UserInfo(userInfo.name);
        newUserInfo.permissions.addAll(userInfo.permissions);

        newUserInfo.fullName = "Ash Ketchum";
        newUserInfo.email = "pikachu@thundershock.pkm";
        auth.updateCurrentUser(null, newUserInfo);
        userInfo = auth.getCurrentUser();
        assertEquals(newUserInfo.fullName, userInfo.fullName);
        assertEquals(newUserInfo.email, userInfo.email);

        newUserInfo.fullName = "Gary Oak";
        newUserInfo.email = "mewtu@mew.pkm";
        auth.updateCurrentUser(null, newUserInfo);
        userInfo = auth.getCurrentUser();
        assertEquals(newUserInfo.fullName, userInfo.fullName);
        assertEquals(newUserInfo.email, userInfo.email);

        newUserInfo.inactive = true;
        assertThrows(RuntimeException.class, () -> auth.updateCurrentUser(null, newUserInfo));
        userInfo = auth.getCurrentUser();
        assertFalse(userInfo.inactive);

        newUserInfo.inactive = false;
        newUserInfo.permissions.clear();
        assertThrows(RuntimeException.class, () -> auth.updateCurrentUser(null, newUserInfo));
        userInfo = auth.getCurrentUser();
        assertFalse(userInfo.inactive);
        assertFalse(userInfo.permissions.isEmpty());
    }

    @Test
    void testCurrentUserGroupLogic(AuthResource auth) {
        UserGroupInfo userGroupInfo = new UserGroupInfo();
        userGroupInfo.name = "TestGroup";
        userGroupInfo.description = "Cool Test Group";
        userGroupInfo.permissions = Set.of(new ScopedPermission(ScopedPermission.Permission.READ));

        AuthAdminResource authAdmin = auth.getAdmin();

        authAdmin.createUserGroup(userGroupInfo);

        SortedSet<UserGroupInfo> allUserGroups = authAdmin.getAllUserGroups();
        userGroupInfo = allUserGroups.stream().filter(g -> g.name.equals("TestGroup")).findAny().get();
        String userGroupId = userGroupInfo.id;

        String currentUserName = auth.getCurrentUser().name;

        assertFalse(auth.getCurrentUserProfile().userGroups.contains(userGroupInfo));
        authAdmin.addUserToGroup(userGroupId, currentUserName);
        assertTrue(auth.getCurrentUserProfile().userGroups.contains(userGroupInfo));
        authAdmin.removeUserFromGroup(userGroupId, currentUserName);
        assertFalse(auth.getCurrentUserProfile().userGroups.contains(userGroupInfo));
    }

    @Test
    void testCurrentUserDeletion(AuthResource auth) {
        AuthAdminResource authAdmin = auth.getAdmin();

        assertThrows(RuntimeException.class, () -> auth.deleteCurrentUser(null));

        UserInfo newUserInfo = new UserInfo("secondadmin");
        newUserInfo.password = "blahblahblah";
        authAdmin.createLocalUser(newUserInfo);

        assertThrows(RuntimeException.class, () -> auth.deleteCurrentUser(null));

        newUserInfo.permissions.add(ScopedPermission.GLOBAL_ADMIN);
        newUserInfo.inactive = true;
        authAdmin.updateUser(newUserInfo);

        assertThrows(RuntimeException.class, () -> auth.deleteCurrentUser(null));

        newUserInfo.inactive = false;
        authAdmin.updateUser(newUserInfo);

        auth.deleteCurrentUser(null);
    }

    @Test
    void testCurrentUserPermissionChange(AuthResource auth) {
        // Create a second global administrator so that the initial global administrator is allowed to downgrade its own permissions itself
        UserInfo secondAdminInfo = new UserInfo("secondadmin");
        secondAdminInfo.password = "blahblahblah";
        secondAdminInfo.permissions.add(ScopedPermission.GLOBAL_ADMIN);
        auth.getAdmin().createLocalUser(secondAdminInfo);

        // Downgrade the initial administrator to WRITE level -> this invalidates the token!
        UserInfo firstAdminInfo = auth.getCurrentUser();
        firstAdminInfo.permissions.clear();
        firstAdminInfo.permissions.add(new ScopedPermission(ScopedPermission.Permission.WRITE));
        auth.updateCurrentUser(null, firstAdminInfo);

        // Attempt to downgrade the initial administrator even further to READ level -> will fail because of token permission mismatch
        firstAdminInfo.permissions.clear();
        firstAdminInfo.permissions.add(new ScopedPermission(ScopedPermission.Permission.READ));
        assertThrows(NotAuthorizedException.class, () -> auth.updateCurrentUser(null, firstAdminInfo));
    }
}
