package io.bdeploy.launcher.cli.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

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
        super(new Dimension(800, 600));
        setTitle("Customize Application Arguments");

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

    /** Creates the widgets shown in the header */
    private JPanel createHeaderArea() {
        JPanel header = new JPanel();
        header.setBackground(Color.WHITE);
        header.setLayout(new GridLayout());
        header.setBorder(new EmptyBorder(10, 10, 10, 10));

        headerText = new JLabel();
        headerText.setFont(headerText.getFont().deriveFont(Font.BOLD, 16f));
        header.add(headerText);

        return header;
    }

    /** Creates the widgets shown in the content */
    private JPanel createContentArea() {
        JPanel content = new JPanel();
        content.setBackground(Color.WHITE);
        content.setLayout(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));

        contentText = new JLabel();
        content.add(contentText, BorderLayout.NORTH);

        textArea = new JTextArea();
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane scrollPane = new JScrollPane(textArea);
        content.add(scrollPane, BorderLayout.CENTER);

        hintText = new JLabel();
        content.add(hintText, BorderLayout.SOUTH);

        return content;
    }

    /** Creates the widgets shown in the footer */
    private JPanel createFooter(JPanel content) {
        JPanel footer = new JPanel();
        footer.setBorder(new EmptyBorder(10, 10, 10, 10));
        footer.setLayout(new BorderLayout(15, 15));

        JPanel actionPanel = new JPanel();
        actionPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        footer.add(actionPanel, BorderLayout.EAST);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(a -> doClose(-1));
        actionPanel.add(cancel);

        JButton launch = new JButton("Launch");
        launch.setFont(launch.getFont().deriveFont(Font.BOLD));
        launch.addActionListener(a -> doClose(0));
        actionPanel.add(launch);

        return footer;
    }

    /**
     * Opens the dialog to customize the arguments of the application
     */
    public boolean customize(String appName, List<String> command) {
        headerText.setText(appName);
        contentText.setText("<html>The following arguments are passed to the application:");
        hintText.setText("<html><i>Hint:</i> Arguments can be <b>added</b>, <b>removed</b> or <b>changed</b> as desired.</html>");

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
