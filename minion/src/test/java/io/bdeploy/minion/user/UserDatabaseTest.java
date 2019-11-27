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
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.AuthService.UserInfo;

@ExtendWith(TestMinion.class)
public class UserDatabaseTest {

    @Test
    void userRoles(MinionRoot root) {
        UserDatabase db = root.getUsers();

        db.updateUser("test", "test", Collections.singletonList(ApiAccessToken.ADMIN_CAPABILITY));

        UserInfo info = db.authenticate("test", "test");
        assertNotNull(info);
        assertNotNull(info.capabilities);
        assertEquals(1, info.capabilities.size());
        assertEquals(ApiAccessToken.ADMIN_CAPABILITY.capability, info.capabilities.get(0).capability);

        db.updateUser("test", null, Collections.emptyList());

        UserInfo updated = db.authenticate("test", "test");
        assertNotNull(updated);
        assertNotNull(updated.capabilities);
        assertTrue(updated.capabilities.isEmpty());
    }

    @Test
    void userRecentlyUsed(MinionRoot root) {
        UserDatabase db = root.getUsers();

        List<String> recently1 = Arrays.asList(new String[] { "a", "b", "c" });

        db.updateUser("test", "test", null);

        db.addRecentlyUsedInstanceGroup("test", "a");
        db.addRecentlyUsedInstanceGroup("test", "b");
        db.addRecentlyUsedInstanceGroup("test", "c");
        assertIterableEquals(recently1, db.getRecentlyUsedInstanceGroups("test"));
    }

    @Test
    void userCleanup(MinionRoot root) {
        UserDatabase db = root.getUsers();

        db.updateUser("test", "test", null);
        for (int i = 0; i < 20; ++i) {
            db.updateUser("test", null, Collections.singletonList(new ScopedCapability("Scope" + i, Capability.ADMIN)));
        }

        BHive hive = root.getHive();
        assertEquals(10, hive.execute(new ManifestListOperation().setManifestName("users/test")).size());
    }

}
