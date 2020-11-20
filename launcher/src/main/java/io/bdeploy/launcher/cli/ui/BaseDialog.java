package io.bdeploy.launcher.cli.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.UIManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.bdeploy.common.util.Threads;

/**
 * Base class for all dialogs. Ensures that a common look-and-feel is used.
 */
public class BaseDialog extends JFrame {

    private static final long serialVersionUID = 1L;

    static {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot set system look&feel", e);
        }
    }

    private final transient Object lock = new Object();
    private int closeReason = 0;

    /**
     * Creates a new centered dialog with the given dimensions
     */
    public BaseDialog(Dimension dimension) {
        setSize(dimension);
        setLayout(new BorderLayout(10, 10));
        setIconImage(WindowHelper.loadImage("/logo128.png"));
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
