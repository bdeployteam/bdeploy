package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.common.TestCliTool.StructuredOutput;
import io.bdeploy.common.TestCliTool.StructuredOutputRow;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.cli.RemoteUserGroupTool;
import io.bdeploy.ui.cli.RemoteUserSelfTool;
import io.bdeploy.ui.cli.RemoteUserTool;
import jakarta.ws.rs.NotAuthorizedException;

@ExtendWith(TestMinion.class)
public class RemoteUserSelfToolCliTest extends BaseMinionCliTest {

    @Test
    void testUserUpdateInfo(RemoteService remote) {
        // Update nothing
        StructuredOutputRow info1_1 = getCurrentUserInfo(remote);
        remote(remote, RemoteUserSelfTool.class, "--update");
        StructuredOutputRow info1_2 = getCurrentUserInfo(remote);
        assertEquals(info1_1.get("FullName"), info1_2.get("FullName"));
        assertEquals(info1_1.get("EMail"), info1_2.get("EMail"));

        // Update full name
        String newName1 = "NewCoolName1";
        StructuredOutputRow info2_1 = getCurrentUserInfo(remote);
        assertNotEquals(newName1, info2_1.get("FullName"));
        remote(remote, RemoteUserSelfTool.class, "--update", "--fullName=" + newName1);
        StructuredOutputRow info2_2 = getCurrentUserInfo(remote);
        assertEquals(newName1, info2_2.get("FullName"));

        // Update email
        String newEmail1 = "new@e.mail1";
        StructuredOutputRow info3_1 = getCurrentUserInfo(remote);
        assertNotEquals(newEmail1, info3_1.get("EMail"));
        remote(remote, RemoteUserSelfTool.class, "--update", "--email=" + newEmail1);
        StructuredOutputRow info3_2 = getCurrentUserInfo(remote);
        assertEquals(newEmail1, info3_2.get("EMail"));

        // Update both
        String newName2 = "NewCoolName2";
        String newEmail2 = "new@e.mail2";
        StructuredOutputRow info4_1 = getCurrentUserInfo(remote);
        assertNotEquals(newName2, info4_1.get("FullName"));
        assertNotEquals(newEmail2, info4_1.get("FullName"));
        remote(remote, RemoteUserSelfTool.class, "--update", "--fullName=" + newName2, "--email=" + newEmail2);
        StructuredOutputRow info4_2 = getCurrentUserInfo(remote);
        assertEquals(newName2, info4_2.get("FullName"));
        assertEquals(newEmail2, info4_2.get("EMail"));
    }

    @Test
    void testUserUpdatePassword(RemoteService remote) {
        assertDoesNotThrow(() -> remote(remote, RemoteUserSelfTool.class, "--updatePassword=newPassword"));
        assertDoesNotThrow(() -> remote(remote, RemoteUserSelfTool.class, "--updatePassword=oldPassword"));
        assertDoesNotThrow(() -> remote(remote, RemoteUserSelfTool.class, "--updatePassword=samePassword"));
        assertDoesNotThrow(() -> remote(remote, RemoteUserSelfTool.class, "--updatePassword=samePassword"));
        assertDoesNotThrow(() -> remote(remote, RemoteUserSelfTool.class, "--updatePassword=oldPassword"));
    }

    @Test
    void testUserLeaveGroup(RemoteService remote) {
        StructuredOutput attempt1 = remote(remote, RemoteUserSelfTool.class, "--leaveGroup=thisGroupDoesNotExist");
        assertEquals("The current user is not in group thisGroupDoesNotExist", attempt1.get(0).get("message"));

        remote(remote, RemoteUserGroupTool.class, "--add=CoolTestGroup", "--admin");
        remote(remote, RemoteUserGroupTool.class, "--group=CoolTestGroup", "--addUser=test");

        String[] groups1 = getCurrentUserInfo(remote).get("Groups").split("; ");
        assertTrue(Arrays.stream(groups1).anyMatch(s -> s.equals("CoolTestGroup (null)")));

        remote(remote, RemoteUserSelfTool.class, "--leaveGroup=CoolTestGroup");

        String[] groups2 = getCurrentUserInfo(remote).get("Groups").split("; ");
        assertTrue(Arrays.stream(groups2).noneMatch(s -> s.equals("CoolTestGroup (null)")));
    }

    @Test
    void testUserRemovePermission(RemoteService remote) {
        assertThrows(RuntimeException.class, () -> remote(remote, RemoteUserSelfTool.class, "--removePermission=admin"));

        remote(remote, RemoteUserTool.class, "--add=admin2", "--password=adminadminadmin", "--admin");

        StructuredOutput attempt2 = remote(remote, RemoteUserSelfTool.class, "--removePermission=admin");
        assertEquals("Success", attempt2.get(0).get("message"));

        assertThrows(NotAuthorizedException.class, () -> remote(remote, RemoteUserSelfTool.class, "--info"));
    }

    @Test
    void testUserDeactivate(RemoteService remote) {
        assertThrows(RuntimeException.class, () -> remote(remote, RemoteUserSelfTool.class, "--deactivate"));

        remote(remote, RemoteUserTool.class, "--add=admin2", "--password=adminadminadmin", "--admin");

        StructuredOutput attempt2 = remote(remote, RemoteUserSelfTool.class, "--deactivate");
        assertEquals("Success", attempt2.get(0).get("message"));

        assertThrows(NotAuthorizedException.class, () -> remote(remote, RemoteUserSelfTool.class, "--info"));
    }

    @Test
    void testUserDelete(RemoteService remote) {
        assertThrows(RuntimeException.class, () -> remote(remote, RemoteUserSelfTool.class, "--delete"));

        remote(remote, RemoteUserTool.class, "--add=admin2", "--password=adminadminadmin", "--admin");

        StructuredOutput attempt2 = remote(remote, RemoteUserSelfTool.class, "--delete");
        assertEquals("Success", attempt2.get(0).get("message"));

        /**
         * TODO Attempting to use the CLI after the user has been deleted SHOULD throw a NotAuthorizedException exception,
         * HOWEVER, due to the way the TokenValidator is currently implemented, we just get a NullPointerException in the
         * RemoteUserSelfTool. Un-comment this check after the TODO in the TokenValidator has been done.
         */
        //assertThrows(NotAuthorizedException.class, () -> remote(remote, RemoteUserSelfTool.class, "--info"));
    }

    private StructuredOutputRow getCurrentUserInfo(RemoteService remote) {
        return remote(remote, RemoteUserSelfTool.class, "--info").get(0);
    }
}
