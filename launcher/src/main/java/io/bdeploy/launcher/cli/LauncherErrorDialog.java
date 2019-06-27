package io.bdeploy.launcher.cli;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Display a plain dialog showing what went wrong.
 */
public class LauncherErrorDialog extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(LauncherErrorDialog.class);

    private static final String SHOW_DETAILS_LABEL = "Show Details";
    private static final String HIDE_MESSAGE_LABEL = "Hide Details";

    static {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            throw new RuntimeException("Cannot set system look&feel", e);
        }
    }

    private final Object lock = new Object();

    /** Flag which page is displayed in the content */
    private boolean messagePage = true;

    /** Contains the detailed error message */
    private JTextArea errorDetails;

    /** Label containing a nice human readable error message */
    private JLabel errorSummary;

    /**
     * Shows the error dialog with a dummy stack-trace. Only useful for testing.
     */
    public static void main(String[] args) {
        LauncherErrorDialog dialog = new LauncherErrorDialog();
        dialog.showError(new RuntimeException());
    }

    /**
     * Creates a new dialog to show an error message to the user
     */
    public LauncherErrorDialog() {
        super("Launcher");
        setSize(600, 300);
        WindowHelper.center(this);
        setIconImage(loadImage("/logo128.png"));
        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent we) {
                doClose();
            }
        });
        setLayout(new BorderLayout(10, 10));

        // Header area displaying icon and text
        JPanel header = new JPanel();
        header.setBackground(new Color(255, 255, 255));
        header.setBorder(new EmptyBorder(10, 10, 10, 10));
        header.setLayout(new FlowLayout());
        {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.anchor = GridBagConstraints.WEST;

            JLabel widget = new JLabel(loadIcon("/error.png", 32, 32));
            header.add(widget);
        }
        {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.fill = GridBagConstraints.HORIZONTAL;

            JLabel widget = new JLabel("Application could not be launched");
            widget.setFont(widget.getFont().deriveFont(Font.BOLD, 16f));
            widget.setForeground(new Color(255, 79, 73));
            header.add(widget);
        }
        add(header, BorderLayout.NORTH);

        // Content are displaying a hint
        JPanel content = new JPanel();
        content.setLayout(new CardLayout());
        content.setBackground(new Color(255, 255, 255));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));
        {
            errorSummary = new JLabel();
            errorSummary.setFont(errorSummary.getFont().deriveFont(Font.BOLD, 12f));
            content.add(errorSummary);
        }
        {
            errorDetails = new JTextArea();
            errorDetails.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            JScrollPane scrollPane = new JScrollPane(errorDetails);
            content.add(scrollPane);
        }
        add(content, BorderLayout.CENTER);

        // Footer displaying buttons
        JPanel footer = new JPanel();
        footer.setBorder(new EmptyBorder(10, 10, 10, 10));
        footer.setLayout(new BorderLayout(15, 15));
        {
            JPanel actionPanel = new JPanel();
            actionPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

            JButton details = new JButton();
            details.setText(SHOW_DETAILS_LABEL);
            details.addActionListener(a -> toggleDetails(content, details));
            actionPanel.add(details);

            JButton clipboard = new JButton();
            clipboard.setIcon(loadIcon("/copy.png", 16, 16));
            clipboard.setToolTipText("Copy details to clipboard");
            clipboard.addActionListener(a -> doCopyToClipboard());
            actionPanel.add(clipboard);
            footer.add(actionPanel, BorderLayout.WEST);

            JButton close = new JButton("Close");
            close.addActionListener(a -> doClose());
            footer.add(close, BorderLayout.EAST);
        }
        add(footer, BorderLayout.PAGE_END);
    }

    /**
     * Opens the dialog to show the error message to the user. Blocks until the user closes the dialog.
     *
     * @param ex the exception that occurred
     */
    public void showError(Throwable ex) {
        setVisible(true);

        // Default error summary
        errorSummary.setText("<html>Unexpected error occured while launching the application. " + //
                "If the problem persists, contact the system administrator.</html>");

        // Detailed error message
        errorDetails.setText(getDetailedErrorMessage(ex));

        // Block until dialog is closed
        waitForExit();
    }

    /**
     * Blocks the current thread until the main window is closed.
     */
    private void waitForExit() {
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Failed to wait until the application is closed", ie);
            }
        }
    }

    /** Reads and returns the embedded image with the given name */
    private static BufferedImage loadImage(String iconName) {
        try (InputStream in = LauncherErrorDialog.class.getResourceAsStream(iconName)) {
            return ImageIO.read(in);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Reads and returns the embedded icon and scales it to the given resolution */
    private static ImageIcon loadIcon(String iconName, int width, int height) {
        BufferedImage image = loadImage(iconName);
        return new ImageIcon(image.getScaledInstance(width, height, BufferedImage.SCALE_SMOOTH));
    }

    /** Returns the detailed error message to be displayed */
    private static String getDetailedErrorMessage(Throwable ex) {
        StringWriter writer = new StringWriter();
        ex.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    /** Disposes the dialog and signals that the main thread can continue */
    private void doClose() {
        dispose();
        synchronized (lock) {
            lock.notify();
        }
    }

    /**
     * Copies the detailed error message to the clipboard
     */
    private void doCopyToClipboard() {
        StringSelection stringSelection = new StringSelection(errorDetails.getText());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    /**
     * Expands or collapses the section showing technical details
     */
    private void toggleDetails(JPanel content, JButton button) {
        this.messagePage = !this.messagePage;

        // Update button text for the user
        if (messagePage) {
            setSize(600, 300);
            button.setText(SHOW_DETAILS_LABEL);
        } else {
            setSize(600, 450);
            button.setText(HIDE_MESSAGE_LABEL);
        }

        // Show next card
        CardLayout layout = (CardLayout) content.getLayout();
        layout.next(content);
    }

}
