package io.bdeploy.launcher.cli.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import org.junit.jupiter.api.Test;

/**
 * Contains unit tests for {@link WindowHelper}.
 */
public class WindowHelperTest {

    private static final int defaultIconSize = 32;

    @Test
    void testIconLoading() {
        String[] icons = { "/arrow_down.png", "/arrow_up.png", "/copy.png", "/customizeAndLaunch.png", "/enable.png",
                "/error.png", "/fixErrors.png", "/launch.png", "/logo16.png", "/logo24.png", "/logo32.png", "/logo48.png",
                "/logo64.png", "/logo128.png", "/logo256.png", "/prune.png", "/refresh.png", "/reinstall.png", "/splash.png",
                "/uninstall.png", "/update.png", "/verify.png" };

        for (String icon : icons) {
            assertThrowsExactly(IllegalArgumentException.class, () -> WindowHelper.loadIcon(icon, 0, 0));
            assertThrowsExactly(IllegalArgumentException.class, () -> WindowHelper.loadIcon(icon, 0, 1));
            assertThrowsExactly(IllegalArgumentException.class, () -> WindowHelper.loadIcon(icon, 1, 0));
            assertNotNull(WindowHelper.loadIcon(icon, defaultIconSize, defaultIconSize));
            assertNotNull(WindowHelper.loadIcon(icon, 1, 1));
            assertNotNull(WindowHelper.loadIcon(icon, 100, 100));
            assertNotNull(WindowHelper.loadIcon(icon, 1000, 1000));
            assertNotNull(WindowHelper.loadIcon(icon, 37, 876));
        }
    }
}
