package io.bdeploy.launcher.cli.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

import com.formdev.flatlaf.extras.FlatSVGIcon;

public class WindowHelper {

    private WindowHelper() {
    }

    /**
     * Places the given window in the center of the default screen.
     *
     * @param window the window to center
     */
    public static void center(Component window) {
        Dimension windowSize = window.getSize();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle bounds = ge.getDefaultScreenDevice().getDefaultConfiguration().getBounds();

        int dx = bounds.x + (bounds.width / 2) - windowSize.width / 2;
        int dy = bounds.y + (bounds.height / 2) - windowSize.height / 2;
        window.setLocation(dx, dy);
    }

    /** Reads and returns the embedded icon. */
    public static FlatSVGIcon loadSvgIcon(String iconName) {
        return new FlatSVGIcon(iconName + ".svg");
    }
}
