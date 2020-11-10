package io.bdeploy.launcher.cli.ui.browser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingWorker.StateValue;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;
import io.bdeploy.launcher.cli.ClientSoftwareManifest;
import io.bdeploy.launcher.cli.ui.BaseDialog;
import io.bdeploy.launcher.cli.ui.WindowHelper;

/**
 * A dialog that lists all locally available applications
 */
public class BrowserDialog extends BaseDialog {

    private static final long serialVersionUID = 1L;

    private final Path rootDir;
    private final BrowserDialogTableModel model = new BrowserDialogTableModel();

    private final TableRowSorter<BrowserDialogTableModel> sortModel = new TableRowSorter<>(model);
    private final JTable table = new JTable(model);

    private JButton launchButton;
    private JButton refreshButton;
    private JButton uninstallButton;

    private JMenuItem launchItem;
    private JMenuItem refreshItem;
    private JMenuItem uninstallItem;

    private JProgressBar progressBar;

    public BrowserDialog(Path rootDir) {
        super(new Dimension(1024, 768));
        this.rootDir = rootDir;
        setTitle("Client Applications");

        // Header area displaying a search field
        JPanel header = createHeader();
        add(header, BorderLayout.PAGE_START);

        // Content displaying the table
        JScrollPane content = createContent();
        add(content, BorderLayout.CENTER);

        // Footer displaying some progress
        JPanel footer = createFooter();
        add(footer, BorderLayout.PAGE_END);
    }

    /**
     * Scans the hive in order to find all available applications. Applications without a descriptor are
     * ignored since we cannot launch them.
     */
    public void searchApps() {
        Path hivePath = rootDir.resolve("bhive");
        if (!hivePath.toFile().isDirectory()) {
            return;
        }
        model.clear();
        try (BHive hive = new BHive(hivePath.toUri(), new ActivityReporter.Null())) {
            ClientSoftwareManifest manifest = new ClientSoftwareManifest(hive);
            model.addAll(manifest.list().stream().filter(mf -> mf.clickAndStart != null).collect(Collectors.toList()));
        }
        doUpdateButtonState();
    }

    /** Creates the widgets shown in the header */
    private JPanel createHeader() {
        JPanel header = new JPanel();
        header.setBackground(Color.WHITE);
        header.setBorder(new EmptyBorder(10, 10, 10, 10));
        header.setLayout(new BorderLayout());

        launchButton = new JButton();
        launchButton.setText("Launch");
        launchButton.setToolTipText("Launches the selected application");
        launchButton.setIcon(WindowHelper.loadIcon("/launch.png", 24, 24));
        launchButton.addActionListener(this::onLaunchButtonClicked);
        launchButton.setBackground(Color.WHITE);

        refreshButton = new JButton();
        refreshButton.setText("Refresh");
        refreshButton.setToolTipText("Contacts the remote server to refresh the properties of the installed applications");
        refreshButton.setIcon(WindowHelper.loadIcon("/refresh.png", 24, 24));
        refreshButton.addActionListener(this::onRefreshButtonClicked);
        refreshButton.setBackground(Color.WHITE);

        uninstallButton = new JButton();
        uninstallButton.setText("Uninstall");
        uninstallButton.setToolTipText("Removes the selected application");
        uninstallButton.setIcon(WindowHelper.loadIcon("/delete.png", 24, 24));
        uninstallButton.addActionListener(this::onUninstallButtonClicked);
        uninstallButton.setBackground(Color.WHITE);

        // Toolbar on the left side
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(Color.WHITE);
        toolbar.add(launchButton);
        toolbar.add(refreshButton);
        toolbar.add(new JToolBar.Separator());
        toolbar.add(uninstallButton);
        header.add(toolbar, BorderLayout.WEST);

        // Search panel on the right side
        JPanel searchPanel = new JPanel();
        searchPanel.setBackground(Color.WHITE);
        searchPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        header.add(searchPanel, BorderLayout.EAST);

        JLabel searchLabel = new JLabel("Search:", JLabel.RIGHT);
        searchLabel.setOpaque(true);
        searchLabel.setBackground(Color.WHITE);
        searchPanel.add(searchLabel);

        JTextField searchField = new JTextField(30);
        searchField.getDocument().addDocumentListener(new SimpleDocumentListener() {

            @Override
            protected void onChanged(DocumentEvent e) {
                onFilterChanged(searchField.getText());
            }
        });
        searchPanel.add(searchField);
        return header;
    }

    /** Creates the widgets shown in the content */
    private JScrollPane createContent() {
        table.setRowHeight(25);
        table.setBackground(Color.WHITE);
        table.setShowHorizontalLines(true);

        // Notify on selection changes
        ListSelectionModel selectionModel = table.getSelectionModel();
        selectionModel.addListSelectionListener(this::onSelectionChanged);

        // Setup a nicer header
        JTableHeader header = table.getTableHeader();
        header.setDefaultRenderer(new BrowserDialogTableHeaderRenderer());

        // Setup default column properties
        TableColumnModel columnModel = table.getColumnModel();
        {
            TableColumn column = columnModel.getColumn(BrowserDialogTableModel.COL_PURPOSE);
            column.setPreferredWidth(25);
            column.setCellRenderer(new BrowserDialogPurposeCellRenderer());
        }
        {
            TableColumn column = columnModel.getColumn(BrowserDialogTableModel.COL_REMOTE);
            column.setPreferredWidth(150);
        }

        // Launch on double click
        table.addMouseListener(new DoubleClickListener());

        // Launch on enter key
        InputMap inputMap = table.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "launch");
        table.getActionMap().put("launch", new EnterAction());

        // Sort and filter
        table.setRowSorter(sortModel);
        RowSorter.SortKey sortKey = new RowSorter.SortKey(BrowserDialogTableModel.COL_APP, SortOrder.ASCENDING);
        sortModel.setMaxSortKeys(1);
        sortModel.setSortKeys(Collections.singletonList(sortKey));

        // Context menu
        launchItem = new JMenuItem(launchButton.getText());
        launchItem.setIcon(WindowHelper.loadIcon("/launch.png", 16, 16));
        launchItem.addActionListener(this::onLaunchButtonClicked);

        uninstallItem = new JMenuItem(uninstallButton.getText());
        uninstallItem.setIcon(WindowHelper.loadIcon("/delete.png", 16, 16));
        uninstallItem.addActionListener(this::onUninstallButtonClicked);

        refreshItem = new JMenuItem(refreshButton.getText());
        refreshItem.setIcon(WindowHelper.loadIcon("/refresh.png", 16, 16));
        refreshItem.addActionListener(this::onRefreshButtonClicked);

        JPopupMenu menu = new JPopupMenu();
        menu.add(launchItem);
        menu.add(refreshItem);
        menu.add(new JSeparator());
        menu.add(uninstallItem);
        table.setComponentPopupMenu(menu);

        // Scroll if there are too many entries
        JScrollPane pane = new JScrollPane(table);
        pane.setBorder(new EmptyBorder(10, 10, 10, 10));
        pane.setBackground(Color.WHITE);
        return pane;
    }

    /** Creates the widgets shown in the footer */
    private JPanel createFooter() {
        JPanel footer = new JPanel();
        footer.setBorder(new EmptyBorder(10, 10, 10, 10));
        footer.setLayout(new BorderLayout(15, 15));

        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        progressBar.setStringPainted(true);
        footer.add(progressBar);
        return footer;
    }

    /** Notification that the search field has changed */
    private void onFilterChanged(String text) {
        sortModel.setRowFilter(new BrowserDialogTableFilter(text.trim().toLowerCase()));
    }

    /** Returns the selected applications */
    private List<ClientSoftwareConfiguration> getSelectedApps() {
        List<ClientSoftwareConfiguration> apps = new ArrayList<>();
        for (int pos : table.getSelectedRows()) {
            int idx = sortModel.convertRowIndexToModel(pos);
            apps.add(model.get(idx));
        }
        return apps;
    }

    /** Notification that the selected app should be launched */
    private void onLaunchButtonClicked(ActionEvent e) {
        ClientSoftwareConfiguration app = getSelectedApps().get(0);
        doLaunch(app);
    }

    /** Notification that the selected app should be removed */
    private void onUninstallButtonClicked(ActionEvent e) {
        ClientSoftwareConfiguration app = getSelectedApps().get(0);
        String appName = app.metadata != null ? app.metadata.appName : app.clickAndStart.applicationId;

        String message = "Are you sure you want to uninstall '" + appName + "'?";
        int result = JOptionPane.showConfirmDialog(this, message, "Uninstall", JOptionPane.YES_NO_OPTION);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        doUninstall(app);
    }

    /** Notification that the selected apps should be refreshed */
    private void onRefreshButtonClicked(ActionEvent e) {
        // Refresh and remember which apps have been added to the hive
        Map<String, ClientSoftwareConfiguration> oldAppMap = model.asMap();
        searchApps();
        Map<String, ClientSoftwareConfiguration> newAppMap = model.asMap();
        newAppMap.keySet().removeAll(oldAppMap.keySet());

        // If nothing is selected we refresh all apps
        // If there is something selected we refresh the selection
        // AND all apps that have been added to the hive
        List<ClientSoftwareConfiguration> apps = getSelectedApps();
        if (apps.isEmpty()) {
            apps.addAll(model.getAll());
        } else {
            apps.addAll(newAppMap.values());
        }
        doRefresh(apps);
    }

    /** Notification that the selected rows have changed */
    private void onSelectionChanged(ListSelectionEvent e) {
        doUpdateButtonState();
    }

    /** Launches the given application */
    private void doLaunch(ClientSoftwareConfiguration app) {
        progressBar.setIndeterminate(true);
        progressBar.setString("Launching '" + app.clickAndStart.applicationId + "'");

        AppLauncher task = new AppLauncher(rootDir, app);
        task.addPropertyChangeListener(this::doUpdateProgessBar);
        task.execute();
    }

    /** Removes the given application */
    private void doUninstall(ClientSoftwareConfiguration app) {
        progressBar.setIndeterminate(true);
        progressBar.setString("Uninstalling '" + app.clickAndStart.applicationId + "'");

        AppUninstaller task = new AppUninstaller(rootDir, app);
        task.addPropertyChangeListener(this::doUpdateProgessBar);
        task.addPropertyChangeListener(this::doRefreshApps);
        task.execute();
    }

    /** Refreshes the given applications */
    private void doRefresh(List<ClientSoftwareConfiguration> apps) {
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setMinimum(0);
        progressBar.setMaximum(apps.size());
        progressBar.setString("Refreshing applications...");

        AppRefresher task = new AppRefresher(rootDir, apps);
        task.addPropertyChangeListener(this::doUpdateProgessBar);
        task.addPropertyChangeListener(this::doRefreshApps);
        task.execute();
    }

    /** Refreshes all installed apps according to the given event */
    private void doRefreshApps(PropertyChangeEvent e) {
        if (e.getNewValue() == StateValue.DONE) {
            searchApps();
        }
    }

    /** Updates the progress bar according to the given event */
    private void doUpdateProgessBar(PropertyChangeEvent e) {
        if (e.getNewValue() == StateValue.STARTED) {
            progressBar.setVisible(true);
        }
        if (e.getNewValue() == StateValue.DONE) {
            progressBar.setVisible(false);
        }
        if (e.getPropertyName().equals("progress")) {
            progressBar.setValue((int) e.getNewValue());
        }
        doUpdateButtonState();
    }

    /** Updates the enabled state of all buttons */
    private void doUpdateButtonState() {
        if (progressBar.isVisible()) {
            launchItem.setEnabled(false);
            refreshButton.setEnabled(false);

            uninstallItem.setEnabled(false);
            uninstallButton.setEnabled(false);

            refreshItem.setEnabled(false);
            refreshButton.setEnabled(false);
            return;
        }

        List<ClientSoftwareConfiguration> apps = getSelectedApps();
        launchButton.setEnabled(apps.size() == 1);
        launchItem.setEnabled(apps.size() == 1);

        uninstallItem.setEnabled(apps.size() == 1);
        uninstallButton.setEnabled(apps.size() == 1);

        refreshItem.setEnabled(true);
        refreshButton.setEnabled(true);
    }

    private class DoubleClickListener extends MouseAdapter {

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() != 2) {
                return;
            }
            List<ClientSoftwareConfiguration> apps = getSelectedApps();
            if (apps.size() != 1) {
                return;
            }
            doLaunch(apps.get(0));
        }

    }

    private class EnterAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e) {
            List<ClientSoftwareConfiguration> apps = getSelectedApps();
            if (apps.size() != 1) {
                return;
            }
            doLaunch(apps.get(0));
        }
    }

}
