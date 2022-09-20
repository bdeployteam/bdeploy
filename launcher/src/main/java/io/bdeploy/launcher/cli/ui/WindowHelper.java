package io.bdeploy.launcher.cli.ui;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

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

    /** Reads and returns the embedded image with the given name */
    public static BufferedImage loadImage(String iconName) {
        try (InputStream in = WindowHelper.class.getResourceAsStream(iconName)) {
            return ImageIO.read(in);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Reads and returns the embedded images with the given name */
    public static List<BufferedImage> loadImages(String... iconNames) {
        List<BufferedImage> images = new ArrayList<>();
        for (String iconName : iconNames) {
            images.add(loadImage(iconName));
        }
        return images;
    }

    /** Reads and returns the embedded icon and scales it to the given resolution */
    public static ImageIcon loadIcon(String iconName, int width, int height) {
        BufferedImage image = loadImage(iconName);
        return new ImageIcon(image.getScaledInstance(width, height, Image.SCALE_SMOOTH));
    }

}
