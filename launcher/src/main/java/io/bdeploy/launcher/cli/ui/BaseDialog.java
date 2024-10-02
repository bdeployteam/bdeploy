package io.bdeploy.launcher.cli.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.EmptyBorder;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;

import io.bdeploy.common.util.Threads;

/**
 * Base class for all dialogs. Ensures that a common look-and-feel is used.
 */
public class BaseDialog extends JFrame {

    private static final String[] WINDOW_IMAGES = { "/logo16.png", "/logo24.png", "/logo32.png", "/logo48.png", "/logo64.png",
            "/logo128.png", "/logo256.png" };

    private static final long serialVersionUID = 1L;

    private static final double GOLDEN_RATIO = 1.618;

    static {
        FlatRobotoFont.install();
        FlatLaf.setPreferredFontFamily(FlatRobotoFont.FAMILY);
        FlatLaf.setPreferredLightFontFamily(FlatRobotoFont.FAMILY_LIGHT);
        FlatLaf.setPreferredSemiboldFontFamily(FlatRobotoFont.FAMILY_SEMIBOLD);
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (UnsupportedLookAndFeelException e) {
            throw new IllegalStateException("Cannot set system look&feel", e);
        }
        UIManager.put("TextComponent.arc", 6); // Makes the borders of input control round, so that it matches the buttons
    }

    protected static final EmptyBorder DEFAULT_EMPTY_BORDER = new EmptyBorder(10, 10, 10, 10);
    protected static final Dimension BUTTON_SEPARATOR_DIMENSION = new Dimension(2, 0);

    private final transient Object lock = new Object();
    private int closeReason = 0;

    /**
     * Creates a new centered dialog with the given width. The height of the dialog will be equal to ~width/φ.
     *
     * @see #BaseDialog(Dimension)
     */
    public BaseDialog(int width) {
        this(new Dimension(width, ((int) Math.round(width / GOLDEN_RATIO))));
    }

    /**
     * Creates a new centered dialog with the given dimensions.
     *
     * @see #BaseDialog(int)
     */
    public BaseDialog(Dimension dimension) {
        setSize(dimension);
        setLayout(new BorderLayout(10, 10));
        setIconImages(Arrays.stream(WINDOW_IMAGES).map(BaseDialog::loadImage).toList());
        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent we) {
                doClose(-1);
            }
        });
        WindowHelper.center(this);
    }

    /** Reads and returns the embedded image with the given name */
    private static BufferedImage loadImage(String iconName) {
        try (InputStream in = WindowHelper.class.getResourceAsStream(iconName)) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                throw new IllegalStateException("Could not load " + iconName);
            }
            return img;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Blocks the current thread until the main window is closed.
     */
    public int waitForExit() {
        Threads.wait(lock, this::isShowing);
        return closeReason;
    }

    /**
     * Invoked when the user closes the dialog.
     */
    protected void doClose(int closeReason) {
        this.closeReason = closeReason;
        dispose();
        synchronized (lock) {
            lock.notifyAll();
        }
    }
}
