package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.security.GeneralSecurityException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.api.remote.v1.dto.CredentialsApi;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.minion.TestMinion;
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

}
