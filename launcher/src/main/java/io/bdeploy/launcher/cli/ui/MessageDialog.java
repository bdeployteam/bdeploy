package io.bdeploy.launcher.cli.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

/**
 * Base class providing a header, content and footer area.
 */
public class MessageDialog extends BaseDialog {

    private static final long serialVersionUID = 1L;

    private static final String SHOW_DETAILS_LABEL = "Show Details";
    private static final String HIDE_MESSAGE_LABEL = "Hide Details";
    private static final Dimension DEFAULT_DIMENSION = new Dimension(650, 300);
    private static final Dimension DETAIL_DIMENSION = new Dimension(650, 450);

    /** Flag which page is displayed in the content */
    private boolean messagePage = true;

    /** Contains the detailed error message */
    protected JTextArea errorDetails;

    /** Label containing a nice human readable error message */
    protected JLabel errorSummary;

    /** Label containing a more technical error message */
    protected JTextArea errorMessage;

    /** Label containing the header icon */
    protected JLabel headerIcon;

    /** Label containing the header text */
    protected JLabel headerText;

    /**
     * Creates a new dialog with a header, content and footer area
     */
    public MessageDialog(String title) {
        super(DEFAULT_DIMENSION);
        setTitle(title);

        // Header area displaying icon and text
        JPanel header = createHeaderArea();
        add(header, BorderLayout.NORTH);

        // Content are displaying a hint
        JPanel content = createContentArea();
        add(content, BorderLayout.CENTER);

        // Footer displaying buttons
        JPanel footer = createFooter(content);
        add(footer, BorderLayout.PAGE_END);
    }

    /**
     * Sets the icon displayed in the header
     */
    public void setHeaderIcon(ImageIcon icon) {
        headerIcon.setIcon(icon);
    }

    /**
     * Sets the icon displayed in the header
     */
    public void setHeaderText(String text) {
        headerText.setText(text);
    }

    /**
     * Sets a short summary what happened.
     */
    public void setSummary(String text) {
        errorSummary.setText(text);
    }

    /**
     * Sets a short more detailed summary message.
     */
    public void setDetailsSummary(String text) {
        this.errorMessage.setText(text);
    }

    /**
     * Sets the message that is displayed in the expandable detail area.
     */
    public void setDetails(String text) {
        errorDetails.setText(text);
    }

    /** Creates the widgets shown in the header */
    private JPanel createHeaderArea() {
        JPanel header = new JPanel();
        header.setBackground(Color.WHITE);
        header.setBorder(DEFAULT_EMPTY_BORDER);
        header.setLayout(new FlowLayout());

        headerIcon = new JLabel();
        header.add(headerIcon);

        headerText = new JLabel();
        headerText.setFont(headerText.getFont().deriveFont(Font.BOLD, 16f));
        headerText.setForeground(new Color(255, 79, 73));
        header.add(headerText);

        return header;
    }

    /** Creates the widgets shown in the content */
    private JPanel createContentArea() {
        JPanel content = new JPanel();
        content.setLayout(new CardLayout());
        content.setBackground(Color.WHITE);
        content.setBorder(new EmptyBorder(10, 10, 10, 10));

        errorSummary = new JLabel();
        errorSummary.setFont(errorSummary.getFont().deriveFont(Font.BOLD, 12f));
        content.add(errorSummary);

        JPanel errorContainer = new JPanel();
        errorContainer.setBackground(Color.WHITE);
        errorContainer.setBorder(DEFAULT_EMPTY_BORDER);
        errorContainer.setLayout(new BorderLayout(15, 15));

        errorMessage = new JTextArea();
        errorMessage.setEditable(false);
        errorMessage.setLineWrap(true);
        errorMessage.setFont(errorSummary.getFont().deriveFont(Font.BOLD, 12f));
        errorContainer.add(errorMessage, BorderLayout.PAGE_START);

        errorDetails = new JTextArea();
        errorDetails.setEditable(false);
        errorDetails.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane scrollPane = new JScrollPane(errorDetails);
        errorContainer.add(scrollPane, BorderLayout.CENTER);

        content.add(errorContainer);
        return content;
    }

    /** Creates the widgets shown in the footer */
    private JPanel createFooter(JPanel content) {
        JPanel footer = new JPanel();
        footer.setBorder(DEFAULT_EMPTY_BORDER);
        footer.setLayout(new BorderLayout(15, 15));

        JPanel actionPanel = new JPanel();
        actionPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

        JButton details = new JButton();
        details.setText(SHOW_DETAILS_LABEL);
        details.addActionListener(a -> toggleDetails(content, details));
        actionPanel.add(details);

        JButton clipboard = new JButton();
        clipboard.setIcon(WindowHelper.loadIcon("/copy.png", 16, 16));
        clipboard.setToolTipText("Copy to Clipboard");
        clipboard.addActionListener(a -> doCopyToClipboard());
        actionPanel.add(clipboard);
        footer.add(actionPanel, BorderLayout.WEST);

        JButton close = new JButton("Close");
        close.addActionListener(a -> doClose(0));
        footer.add(close, BorderLayout.EAST);

        return footer;
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
            setSize(DEFAULT_DIMENSION);
            button.setText(SHOW_DETAILS_LABEL);
        } else {
            setSize(DETAIL_DIMENSION);
            button.setText(HIDE_MESSAGE_LABEL);
        }

        // Show next card
        CardLayout layout = (CardLayout) content.getLayout();
        layout.next(content);
    }
}
