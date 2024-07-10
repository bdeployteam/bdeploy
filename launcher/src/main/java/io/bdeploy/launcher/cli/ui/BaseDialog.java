package io.bdeploy.launcher.cli.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.Threads;

/**
 * Base class for all dialogs. Ensures that a common look-and-feel is used.
 */
public class BaseDialog extends JFrame {

    private static final String[] WINDOW_IMAGES = { "/logo16.png", "/logo24.png", "/logo32.png", "/logo48.png", "/logo64.png",
            "/logo128.png", "/logo256.png" };

    private static final long serialVersionUID = 1L;

    static {
        try {
            if (OsHelper.getRunningOs() == OperatingSystem.WINDOWS) {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } else {
                // looks a little ... sub-optimal but prevents GTK high contrast legacy themes from
                // destroying the UI color wise.
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot set system look&feel", e);
        }
    }

    protected static final EmptyBorder DEFAULT_EMPTY_BORDER = new EmptyBorder(10, 10, 10, 10);

    private final transient Object lock = new Object();
    private int closeReason = 0;

    /**
     * Creates a new centered dialog with the given dimensions
     */
    public BaseDialog(Dimension dimension) {
        setSize(dimension);
        setLayout(new BorderLayout(10, 10));
        setIconImages(WindowHelper.loadImages(WINDOW_IMAGES));
        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent we) {
                doClose(-1);
            }
        });
        WindowHelper.center(this);
    }

    /**
     * Blocks the current thread until the main window is closed.
     */
    @SuppressFBWarnings(value = { "UW_UNCOND_WAIT", "WA_NOT_IN_LOOP" })
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
