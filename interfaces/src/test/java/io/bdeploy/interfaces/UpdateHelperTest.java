package io.bdeploy.interfaces;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.Version;
import io.bdeploy.common.util.OsHelper;

class UpdateHelperTest {

    @Test
    void testFilteredStream() {
        var keys = List.of(Manifest.Key.parse("key/windows:1.0.0"), Manifest.Key.parse("key/linux:1.0.0"),
                Manifest.Key.parse("key/linux_aarch64:1.0.0"));

        var filteredOldServer = filter(keys, new Version(4, 0, 0, null));
        var filteredCurrentServer = filter(keys, new Version(7, 7, 0, null));
        var filteredFutureServer = filter(keys, new Version(7, 8, 0, null));

        assertFalse(filteredOldServer.contains(OsHelper.OperatingSystem.LINUX_AARCH64));
        assertTrue(filteredCurrentServer.contains(OsHelper.OperatingSystem.LINUX_AARCH64));
        assertTrue(filteredFutureServer.contains(OsHelper.OperatingSystem.LINUX_AARCH64));

        assertTrue(filteredOldServer.contains(OsHelper.OperatingSystem.LINUX));
        assertTrue(filteredCurrentServer.contains(OsHelper.OperatingSystem.LINUX));
        assertTrue(filteredFutureServer.contains(OsHelper.OperatingSystem.LINUX));

        assertTrue(filteredOldServer.contains(OsHelper.OperatingSystem.WINDOWS));
        assertTrue(filteredCurrentServer.contains(OsHelper.OperatingSystem.WINDOWS));
        assertTrue(filteredFutureServer.contains(OsHelper.OperatingSystem.WINDOWS));
    }

    List<OsHelper.OperatingSystem> filter(List<Manifest.Key> keys, Version version) {
        return UpdateHelper.streamFilteredKeys(keys, version).map(ScopedManifestKey::getOperatingSystem).toList();
    }

}
