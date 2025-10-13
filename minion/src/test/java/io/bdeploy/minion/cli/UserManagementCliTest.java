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
import jakarta.ws.rs.NotAuthorizedException;

@ExtendWith(TestMinion.class)
class UserManagementCliTest extends BaseMinionCliTest {

    private static final String admin2Username = "secondadmin";
    private static final String userUsername = "normaluser";

    @Test
    void testUserActivationAndInactivation(RemoteService remote) {
        String admin1Username = setupTestUsers(remote);

        // Get data of all 3 users
        StructuredOutputRow admin1data = getUserRowByName(remote, admin1Username);
        StructuredOutputRow admin2data = getUserRowByName(remote, admin2Username);
        StructuredOutputRow userData = getUserRowByName(remote, userUsername);

        // Check if all of them are active
        assertEquals("", admin1data.get("Inact"));
        assertEquals("", admin2data.get("Inact"));
        assertEquals("", userData.get("Inact"));

        // Set the second administrator user inactive
        remote(remote, RemoteUserTool.class, "--update=" + admin2Username, "--inactive");

        // Check if the second administrator actually got set to inactive
        admin2data = getUserRowByName(remote, admin2Username);
        assertEquals("*", admin2data.get("Inact"));

        // Set the second administrator user inactive again
        remote(remote, RemoteUserTool.class, "--update=" + admin2Username, "--inactive");

        // Check if the second administrator is still inactive
        admin2data = getUserRowByName(remote, admin2Username);
        assertEquals("*", admin2data.get("Inact"));

        // Set the second administrator user active
        remote(remote, RemoteUserTool.class, "--update=" + admin2Username, "--active");

        // Check if the second administrator actually got set to active
        admin2data = getUserRowByName(remote, admin2Username);
        assertEquals("", admin2data.get("Inact"));

        // Set the second administrator user inactive again
        remote(remote, RemoteUserTool.class, "--update=" + admin2Username, "--inactive");

        // Check if the second administrator is inactive again
        admin2data = getUserRowByName(remote, admin2Username);
        assertEquals("*", admin2data.get("Inact"));
    }

    @Test
    void testUserPermissionManagement(RemoteService remote) {
        String admin1Username = setupTestUsers(remote);

        StructuredOutputRow admin2data;
        StructuredOutputRow userData;

        // Create explicit remote services for the new users
        RemoteService admin1Remote = getRemoteService(remote, admin1Username);
        RemoteService admin2Remote = getRemoteService(remote, admin2Username);
        RemoteService userRemote = getRemoteService(remote, userUsername);

        // Confirm that the tokens have the correct privileges
        assertDoesNotThrow(() -> remote(admin1Remote, RemoteUserTool.class, "--list"));
        assertDoesNotThrow(() -> remote(admin2Remote, RemoteUserTool.class, "--list"));
        assertThrows(NotAuthorizedException.class, () -> remote(userRemote, RemoteUserTool.class, "--list"));

        // Remove the permission of the second administrator
        remote(remote, RemoteUserTool.class, "--update=" + admin2Username, "--removePermission=ADMIN");

        // Check if the permission actually got removed
        admin2data = getUserRowByName(remote, admin2Username);
        assertEquals("[]", admin2data.get("Permissions"));

        // Check if the token actually lost administrative power
        assertThrows(NotAuthorizedException.class, () -> remote(admin2Remote, RemoteUserTool.class, "--list"));

        // Promote the permission of the user
        remote(remote, RemoteUserTool.class, "--update=" + userUsername, "--permission=ADMIN");

        // Check if the permission actually got added
        userData = getUserRowByName(remote, userUsername);
        assertEquals("[ADMIN (<<GLOBAL>>)]", userData.get("Permissions"));

        // Check if the token actually gained administrative power
        assertDoesNotThrow(() -> remote(userRemote, RemoteUserTool.class, "--list"));

        // Set the user to inactive
        remote(remote, RemoteUserTool.class, "--update=" + userUsername, "--inactive=true");

        // Check if the user actually got set to inactive
        userData = getUserRowByName(remote, userUsername);
        assertEquals("*", userData.get("Inact"));

        // Check if the token actually lost administrative power
        assertThrows(NotAuthorizedException.class, () -> remote(userRemote, RemoteUserTool.class, "--list"));
    }

    @Test
    void testUserDeletion(RemoteService remote) {
        String admin1Username = setupTestUsers(remote);

        // Set the second administrator user inactive
        remote(remote, RemoteUserTool.class, "--update=" + admin2Username, "--inactive");

        // Verify that the only active global administrator cannot be deleted
        remote(remote, RemoteUserTool.class, "--remove=" + admin1Username);
        assertEquals(3, remote(remote, RemoteUserTool.class, "--list").size());

        // Delete the second administrator user
        remote(remote, RemoteUserTool.class, "--remove=" + admin2Username);
        assertEquals(2, remote(remote, RemoteUserTool.class, "--list").size());

        // Verify that the last global administrator cannot be deleted
        remote(remote, RemoteUserTool.class, "--remove=" + admin1Username);
        assertEquals(2, remote(remote, RemoteUserTool.class, "--list").size());

        // Delete the normal user
        remote(remote, RemoteUserTool.class, "--remove=" + userUsername);
        assertEquals(1, remote(remote, RemoteUserTool.class, "--list").size());
    }

    private String setupTestUsers(RemoteService remote) {
        // Ensure that exactly 1 user exists
        assertEquals(1, remote(remote, RemoteUserTool.class, "--list").size());

        // Get the name of the initial administrator and ensure that there are no name clashes
        String initialAdminUsername = remote(remote, RemoteUserTool.class, "--list").get(0).get("Username");
        assertNotEquals(initialAdminUsername, admin2Username);
        assertNotEquals(initialAdminUsername, userUsername);

        StructuredOutputRow admin1data;
        StructuredOutputRow admin2data;
        StructuredOutputRow userData;

        // Ensure admin1 is a global administrator
        admin1data = getUserRowByName(remote, initialAdminUsername);
        assertEquals("[ADMIN (<<GLOBAL>>)]", admin1data.get("Permissions"));

        // Create a second global administrator
        remote(remote, RemoteUserTool.class, "--add=" + admin2Username, "--password=adminadminadmin", "--admin");

        // Ensure admin2 is a global administrator
        admin2data = getUserRowByName(remote, admin2Username);
        assertEquals("[ADMIN (<<GLOBAL>>)]", admin2data.get("Permissions"));

        // Create a normal user
        remote(remote, RemoteUserTool.class, "--add=" + userUsername, "--password=useruseruser");

        // Ensure that user has no permissions
        userData = getUserRowByName(remote, userUsername);
        assertEquals("[]", userData.get("Permissions"));

        return initialAdminUsername;
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
