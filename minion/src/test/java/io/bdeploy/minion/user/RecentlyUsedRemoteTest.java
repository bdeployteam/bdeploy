package io.bdeploy.minion.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.AuthResource;
import io.bdeploy.ui.api.InstanceGroupResource;

@ExtendWith(TestMinion.class)
public class RecentlyUsedRemoteTest {

    @Test
    void recentGroups(InstanceGroupResource root, AuthResource auth, MinionRoot mr) {
        // user must exist in the database
        mr.getUsers().updateUser(System.getProperty("user.name"), "test",
                Collections.singletonList(ApiAccessToken.ADMIN_CAPABILITY));

        assertTrue(auth.getRecentlyUsedInstanceGroups().isEmpty());

        InstanceGroupConfiguration group = new InstanceGroupConfiguration();
        group.name = "group1";
        group.description = "Test";

        for (int i = 0; i < 20; ++i) {
            group.name = "group" + i;
            root.create(group);
        }

        assertEquals(20, root.list().size());

        for (int i = 0; i < 20; ++i) {
            root.getInstanceResource("group" + i).list();
        }

        List<String> expected = new ArrayList<>();
        for (int i = 10; i < 20; ++i) {
            expected.add("group" + i);
        }

        Collections.reverse(expected);
        assertIterableEquals(expected, auth.getRecentlyUsedInstanceGroups());

        for (int i = 19; i >= 10; --i) {
            root.getInstanceResource("group" + i).list();
        }

        Collections.reverse(expected);
        assertIterableEquals(expected, auth.getRecentlyUsedInstanceGroups());
    }

}
