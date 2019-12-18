package io.bdeploy.launcher.cli.ui;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;

public class WindowHelper {

    private WindowHelper() {
    }

    /**
     * Places the given window in the center of the default screen.
     *
     * @param window the window to center
     */
    public static void center(Window window) {
        Dimension windowSize = window.getSize();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle bounds = ge.getDefaultScreenDevice().getDefaultConfiguration().getBounds();

        int dx = bounds.x + (bounds.width / 2) - windowSize.width / 2;
        int dy = bounds.y + (bounds.height / 2) - windowSize.height / 2;
        window.setLocation(dx, dy);
    }

}
