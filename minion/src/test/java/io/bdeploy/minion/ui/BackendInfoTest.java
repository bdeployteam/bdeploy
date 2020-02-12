package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.BackendInfoResource;

@ExtendWith(TestMinion.class)
public class BackendInfoTest {

    @Test
    void testVersion(BackendInfoResource rsrc) {
        assertEquals(VersionHelper.getVersion(), rsrc.getVersion().version);
    }

}
