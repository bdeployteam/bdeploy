package io.bdeploy.launcher.cli.ui.browser.table;

import java.awt.Color;

/**
 * Contains constants for {@link Color colors} which are used by the browser dialog table.
 */
class BrowserDialogTableCellColorConstants {

    private BrowserDialogTableCellColorConstants() {
    }

    static final Color DISABLED = Color.LIGHT_GRAY;
    static final Color ENABLED = Color.GREEN;
    static final Color PAY_ATTENTION = new Color(255, 255, 140);
    static final Color COULD_NOT_CALCULATE = Color.RED;

    static final Color PURPOSE_DEVELOPMENT = new Color(51, 170, 0);
    static final Color PURPOSE_TEST = new Color(51, 170, 255);
    static final Color PURPOSE_PRODUCTIVE = Color.WHITE;
}
