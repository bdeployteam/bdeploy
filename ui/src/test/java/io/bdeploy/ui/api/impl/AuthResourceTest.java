package io.bdeploy.ui.api.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.GeneralSecurityException;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.ui.TestUiBackendServer;
import io.bdeploy.ui.api.AuthResource;
import io.bdeploy.ui.dto.CredentialsDto;

@ExtendWith(TestUiBackendServer.class)
public class AuthResourceTest {

    @Test
    void testAuth(AuthResource auth, TestUiBackendServer backend) throws GeneralSecurityException {
        // mocked auth service expects user == password
        Response notAuth = auth.authenticate(new CredentialsDto("some", "value"));
        assertEquals(401, notAuth.getStatus());

        Response resp = auth.authenticate(new CredentialsDto("same", "same"));
        String token = resp.readEntity(String.class);
        ApiAccessToken decoded = SecurityHelper.getInstance().getVerifiedPayload(token, ApiAccessToken.class,
                backend.getServerStore());

        assertNotNull(decoded);
        assertTrue(decoded.getIssuedTo().equals("same"));
    }

}
