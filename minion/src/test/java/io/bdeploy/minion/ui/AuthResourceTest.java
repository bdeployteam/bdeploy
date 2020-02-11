package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.security.GeneralSecurityException;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.AuthResource;
import io.bdeploy.ui.dto.CredentialsDto;

@ExtendWith(TestMinion.class)
public class AuthResourceTest {

    @Test
    void testAuth(AuthResource auth, TestMinion backend) throws GeneralSecurityException {
        Response notAuth = auth.authenticate(new CredentialsDto("some", "value"));
        assertEquals(401, notAuth.getStatus());

        Response resp = auth.authenticate(new CredentialsDto("Test", "Test"));
        String token = resp.readEntity(String.class);
        ApiAccessToken decoded = SecurityHelper.getInstance().getVerifiedPayload(token, ApiAccessToken.class,
                backend.getKeyStore());

        assertNotNull(decoded);
        assertEquals("Test", decoded.getIssuedTo());
    }

}
