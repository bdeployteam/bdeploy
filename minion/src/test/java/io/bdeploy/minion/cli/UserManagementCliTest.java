package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.common.TestCliTool.StructuredOutput;
import io.bdeploy.common.TestCliTool.StructuredOutputRow;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.cli.RemoteUserTool;
import jakarta.ws.rs.ForbiddenException;

@ExtendWith(TestMinion.class)
class UserManagementCliTest extends BaseMinionCliTest {

    @Test
    void testUserCreationAndPermissionManagement(RemoteService remote) {
        // Ensure that exactly 1 user exists
        assertEquals(1, remote(remote, RemoteUserTool.class, "--list").size());

        String admin1Username = remote(remote, RemoteUserTool.class, "--list").get(0).get("Username");
        String admin2Username = "secondadmin";
        String userUsername = "normaluser";
        StructuredOutputRow admin1data;
        StructuredOutputRow admin2data;
        StructuredOutputRow userData;

        // Ensure that they are a global administrator
        admin1data = getUserRowByName(remote, admin1Username);
        assertEquals("[ADMIN (<<GLOBAL>>)]", admin1data.get("Permissions"));

        // Create a second global administrator
        assertNotEquals(admin2Username, admin1Username);
        remote(remote, RemoteUserTool.class, "--add=" + admin2Username, "--password=adminadminadmin", "--admin");

        // Ensure that they are a global administrator
        admin2data = getUserRowByName(remote, admin2Username);
        assertEquals("[ADMIN (<<GLOBAL>>)]", admin2data.get("Permissions"));

        // Create a normal user
        assertNotEquals(userUsername, admin1Username);
        remote(remote, RemoteUserTool.class, "--add=" + userUsername, "--password=useruseruser");

        // Ensure that they have no permissions
        userData = getUserRowByName(remote, userUsername);
        assertEquals("[]", userData.get("Permissions"));

        // Create explicit remote services for the new users
        RemoteService admin1Remote = getRemoteService(remote, admin1Username);
        RemoteService admin2Remote = getRemoteService(remote, admin2Username);
        RemoteService userRemote = getRemoteService(remote, userUsername);

        // Confirm that the tokens have the correct privileges
        assertDoesNotThrow(() -> remote(admin1Remote, RemoteUserTool.class, "--list"));
        assertDoesNotThrow(() -> remote(admin2Remote, RemoteUserTool.class, "--list"));
        assertThrows(ForbiddenException.class, () -> remote(userRemote, RemoteUserTool.class, "--list"));

        // Promote the permission of the user
        remote(remote, RemoteUserTool.class, "--update=" + userUsername, "--permission=ADMIN");

        // Check if the permission actually got added
        userData = getUserRowByName(remote, userUsername);
        assertEquals("[ADMIN (<<GLOBAL>>)]", userData.get("Permissions"));
    }

    private StructuredOutputRow getUserRowByName(RemoteService remote, String username) {
        var list = remote(remote, RemoteUserTool.class, "--list");
        return list.getAll().stream().filter(row -> row.get("Username").equals(username)).findAny().get();
    }

    private RemoteService getRemoteService(RemoteService baseRemote, String username) {
        StructuredOutput admin2TokenOutput = remote(baseRemote, RemoteUserTool.class, "--createToken=" + username);
        return new RemoteService(baseRemote.getUri(), admin2TokenOutput.getRawOutput()[4]);
    }
}
