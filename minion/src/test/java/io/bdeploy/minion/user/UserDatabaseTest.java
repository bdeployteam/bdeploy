package io.bdeploy.minion.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.ApiAccessToken.Capability;
import io.bdeploy.common.security.ApiAccessToken.ScopedCapability;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestMinion;

@ExtendWith(TestMinion.class)
public class UserDatabaseTest {

    @Test
    void userRoles(MinionRoot root) {
        UserDatabase db = root.getUsers();

        db.createLocalUser("test", "test", Collections.singletonList(ApiAccessToken.ADMIN_CAPABILITY));

        UserInfo info = db.authenticate("test", "test");
        assertNotNull(info);
        assertNotNull(info.capabilities);
        assertEquals(1, info.capabilities.size());
        assertEquals(ApiAccessToken.ADMIN_CAPABILITY.capability, info.capabilities.get(0).capability);

        info.capabilities.clear();
        info.fullName = "Test User";
        info.email = "test.user@example.com";
        db.updateUserInfo(info);

        UserInfo updated = db.authenticate("test", "test");
        assertNotNull(updated);
        assertNotNull(updated.capabilities);
        assertTrue(updated.capabilities.isEmpty());

        assertEquals("Test User", updated.fullName);
        assertEquals("test.user@example.com", updated.email);
    }

    @Test
    void userRecentlyUsed(MinionRoot root) {
        UserDatabase db = root.getUsers();

        List<String> recently1 = Arrays.asList(new String[] { "a", "b", "c" });

        db.createLocalUser("test", "test", null);

        db.addRecentlyUsedInstanceGroup("test", "a");
        db.addRecentlyUsedInstanceGroup("test", "b");
        db.addRecentlyUsedInstanceGroup("test", "c");
        assertIterableEquals(recently1, db.getRecentlyUsedInstanceGroups("test"));
    }

    @Test
    void userCleanup(MinionRoot root) {
        UserDatabase db = root.getUsers();

        db.createLocalUser("test", "test", null);
        for (int i = 0; i < 20; ++i) {
            UserInfo u = db.findUser("test");
            u.capabilities.add(new ScopedCapability("Scope" + i, Capability.ADMIN));
            db.updateUserInfo(u);
        }

        BHive hive = root.getHive();
        assertEquals(10, hive.execute(new ManifestListOperation().setManifestName("users/test")).size());
    }

}
