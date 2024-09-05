package io.bdeploy.launcher.cli.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Contains unit tests for {@link WindowHelper}.
 */
class WindowHelperTest {

    /**
     * Note that the actual testing capabilities of this test are very limited. WindowHelper#loadSvgIcon _never_ returns
     * <code>null</code>. A failed load would just result in a be a red icon.
     */
    @Test
    void testIconLoading() {
        String[] icons = { "arrow_down", "arrow_up", "copy", "customizeAndLaunch", "enable", "error", "fixErrors", "launch",
                "logo16", "logo24", "logo32", "logo48", "logo64", "logo128", "logo256", "prune", "refresh", "reinstall", "splash",
                "splash-new", "uninstall", "update", "verify" };

        for (String icon : icons) {
            assertNotNull(WindowHelper.loadSvgIcon(icon));
        }
    }
}
