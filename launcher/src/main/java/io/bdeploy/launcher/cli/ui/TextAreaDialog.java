package io.bdeploy.launcher.cli.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.google.common.base.Splitter;

/**
 * A dialog with a text area to customize arguments of the application to launch.
 */
public class TextAreaDialog extends BaseDialog {

    private static final long serialVersionUID = 1L;

    private JLabel headerText;
    private JLabel contentText;
    private JTextArea textArea;
    private JLabel hintText;

    public TextAreaDialog() {
        super(800);
        setTitle("Customize Application Arguments");

        // Header area displaying icon and text
        JPanel header = createHeaderArea();
        add(header, BorderLayout.PAGE_START);

        // Content are displaying a hint
        JPanel content = createContentArea();
        add(content, BorderLayout.CENTER);

        // Footer displaying buttons
        JPanel footer = createFooter();
        add(footer, BorderLayout.PAGE_END);
    }

    /** Creates the widgets shown in the header */
    private JPanel createHeaderArea() {
        headerText = new JLabel();
        headerText.setFont(headerText.getFont().deriveFont(Font.BOLD, 16F));

        JPanel header = new JPanel();
        header.setLayout(new GridLayout());
        header.setBorder(DEFAULT_EMPTY_BORDER);
        header.add(headerText);
        return header;
    }

    /** Creates the widgets shown in the content */
    private JPanel createContentArea() {
        contentText = new JLabel();
        textArea = new JTextArea();
        hintText = new JLabel();
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

        JPanel content = new JPanel();
        content.setLayout(new BorderLayout(10, 10));
        content.setBorder(DEFAULT_EMPTY_BORDER);
        content.add(contentText, BorderLayout.PAGE_START);
        content.add(new JScrollPane(textArea), BorderLayout.CENTER);
        content.add(hintText, BorderLayout.PAGE_END);
        return content;
    }

    /** Creates the widgets shown in the footer */
    private JPanel createFooter() {
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(a -> doClose(-1));

        JButton launch = new JButton("Launch");
        launch.setFont(launch.getFont().deriveFont(Font.BOLD));
        launch.addActionListener(a -> doClose(0));

        JPanel actionPanel = new JPanel();
        actionPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actionPanel.add(cancel);
        actionPanel.add(Box.createRigidArea(BUTTON_SEPARATOR_DIMENSION));
        actionPanel.add(launch);

        JPanel footer = new JPanel();
        footer.setBorder(DEFAULT_EMPTY_BORDER);
        footer.setLayout(new BorderLayout(15, 15));
        footer.add(actionPanel, BorderLayout.LINE_END);
        return footer;
    }

    /**
     * Opens the dialog to customize the arguments of the application
     */
    public boolean customize(String appName, List<String> command) {
        headerText.setText(appName);
        contentText.setText("<html>The following arguments are passed to the application:");
        hintText.setText(
                "<html><i>Hint:</i> Arguments can be <b>added</b>, <b>removed</b> and <b>changed</b> as desired. Each line is treated as a single argument.</html>");

        // Extract the arguments and prepare for displaying
        List<String> args = command.subList(1, command.size());
        String text = args.stream().collect(Collectors.joining("\n"));
        textArea.setText(text);
        args.clear();

        // Show dialog
        setVisible(true);
        int closeReason = waitForExit();
        if (closeReason != 0) {
            return false;
        }

        // Append the modified arguments
        text = textArea.getText();
        command.addAll(Splitter.on("\n").trimResults().splitToList(text));
        return true;
    }
}
