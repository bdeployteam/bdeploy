package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

        newUserInfo.fullName = "Ash Ketchum";
        newUserInfo.email = "pikachu@thundershock.pkm";
        auth.updateCurrentUser(newUserInfo);
        userInfo = auth.getCurrentUser();
        assertEquals(newUserInfo.fullName, userInfo.fullName);
        assertEquals(newUserInfo.email, userInfo.email);

        newUserInfo.fullName = "Gary Oak";
        newUserInfo.email = "mewtu@mew.pkm";
        auth.updateCurrentUser(newUserInfo);
        userInfo = auth.getCurrentUser();
        assertEquals(newUserInfo.fullName, userInfo.fullName);
        assertEquals(newUserInfo.email, userInfo.email);
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
}
