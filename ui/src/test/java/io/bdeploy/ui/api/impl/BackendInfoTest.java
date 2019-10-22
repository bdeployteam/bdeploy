package io.bdeploy.ui.api.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.ui.TestUiBackendServer;
import io.bdeploy.ui.api.BackendInfoResource;

@ExtendWith(TestUiBackendServer.class)
public class BackendInfoTest {

    @Test
    void testVersion(BackendInfoResource rsrc) {
        assertEquals(VersionHelper.readVersion(), rsrc.getVersion().version);
    }

}
