package io.bdeploy.launcher.cli.ui.browser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
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
import javax.swing.SwingConstants;
import javax.swing.SwingWorker.StateValue;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.Version;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.launcher.cli.ClientPathHelper;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;
import io.bdeploy.launcher.cli.ClientSoftwareManifest;
import io.bdeploy.launcher.cli.ui.BaseDialog;
import io.bdeploy.launcher.cli.ui.WindowHelper;
import io.bdeploy.launcher.cli.ui.browser.workers.AppLauncher;
import io.bdeploy.launcher.cli.ui.browser.workers.AppRefresher;
import io.bdeploy.launcher.cli.ui.browser.workers.AppReinstaller;
import io.bdeploy.launcher.cli.ui.browser.workers.AppUninstaller;
import io.bdeploy.launcher.cli.ui.browser.workers.AppUpdater;
import io.bdeploy.launcher.cli.ui.browser.workers.FsckTask;
import io.bdeploy.launcher.cli.ui.browser.workers.PruneTask;
import io.bdeploy.launcher.cli.ui.browser.workers.VerifyTask;
import io.bdeploy.logging.audit.RollingFileAuditor;

/**
 * A dialog that lists all locally available applications
 */
public class BrowserDialog extends BaseDialog {

    private static final long serialVersionUID = 1L;

    private final transient Path rootDir;
    private final transient Path bhiveDir;
    private final transient boolean readonlyRoot;
    private final transient Auditor auditor;

    private final BrowserDialogTableModel model;
    private final transient TableRowSorter<BrowserDialogTableModel> sortModel;
    private final JTable table;

    private JButton launchButton;
    private JButton refreshButton;
    private JButton uninstallButton;
    private JButton pruneButton;
    private JButton fsckButton;
    private JButton verifyButton;
    private JButton reinstallButton;

    private JMenuItem launchItem;
    private JMenuItem customizeAndLaunchItem;
    private JMenuItem refreshItem;
    private JMenuItem updateItem;
    private JMenuItem uninstallItem;

    private JProgressBar progressBar;

    public BrowserDialog(Path rootDir, Path userArea) {
        super(new Dimension(1024, 768));

        this.rootDir = rootDir;
        this.bhiveDir = rootDir.resolve("bhive");
        this.readonlyRoot = userArea != null;
        this.auditor = userArea != null ? RollingFileAuditor.getFactory().apply(userArea) : null;

        this.model = new BrowserDialogTableModel(bhiveDir, auditor);
        this.sortModel = new TableRowSorter<>(model);
        this.table = new JTable(model);

        setTitle("Client Applications");

        // Header area displaying a search field
        JPanel header = createHeader();
        add(header, BorderLayout.PAGE_START);

        // Content displaying the table
        JPanel content = createContent();
        add(content, BorderLayout.CENTER);

        // Footer displaying some progress
        JPanel footer = createFooter();
        add(footer, BorderLayout.PAGE_END);

        doUpdateButtonState();
    }

    /**
     * Scans the hive in order to find all available applications. Applications without a descriptor are
     * ignored since we cannot launch them.
     */
    public void searchApps() {
        if (!bhiveDir.toFile().isDirectory()) {
            return;
        }
        model.clear();

        try (BHive hive = new BHive(bhiveDir.toUri(), auditor != null ? auditor : RollingFileAuditor.getFactory().apply(bhiveDir),
                new ActivityReporter.Null())) {
            ClientSoftwareManifest manifest = new ClientSoftwareManifest(hive);
            model.addAll(manifest.list().stream().filter(mf -> mf.clickAndStart != null).toList());
        }
        doUpdateButtonState();
    }

    /** Creates the widgets shown in the header */
    private JPanel createHeader() {
        JPanel header = new JPanel();
        header.setBackground(Color.WHITE);
        header.setBorder(new EmptyBorder(10, 10, 10, 10));
        header.setLayout(new BorderLayout());

        launchButton = createHeaderButton("launch", "Launch", this::onLaunchButtonClicked,//
                "Launch the selected application");

        refreshButton = createHeaderButton("refresh", "Refresh", this::onRefreshButtonClicked,//
                "Update the locally stored information (name, version...) of the selected applications");

        uninstallButton = createHeaderButton("uninstall", "Uninstall", this::onUninstallButtonClicked,//
                "Launch the selected application");

        pruneButton = createHeaderButton("prune", "Prune", this::onPruneButtonClicked,//
                "Remove the selected application");

        fsckButton = createHeaderButton("fixErrors", "Fix Errors", this::onFsckButtonClicked,//
                "Fix any errors in the BHive");

        verifyButton = createHeaderButton("verify", "Verify", this::onVerifyButtonClicked,//
                "Check if selected application has missing or modified files");

        reinstallButton = createHeaderButton("reinstall", "Reinstall", this::onReinstallButtonClicked,//
                "Reinstall the selected application");

        // Toolbar on the left side
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(Color.WHITE);
        toolbar.add(launchButton);
        toolbar.add(refreshButton);
        toolbar.add(new JToolBar.Separator());
        toolbar.add(uninstallButton);
        toolbar.add(new JToolBar.Separator());
        toolbar.add(pruneButton);
        toolbar.add(fsckButton);
        toolbar.add(new JToolBar.Separator());
        toolbar.add(verifyButton);
        toolbar.add(reinstallButton);
        header.add(toolbar, BorderLayout.WEST);

        // Search panel on the right side
        JPanel searchPanel = new JPanel();
        searchPanel.setBackground(Color.WHITE);
        searchPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        header.add(searchPanel, BorderLayout.EAST);

        JLabel searchLabel = new JLabel("Search:", SwingConstants.RIGHT);
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

    private static JButton createHeaderButton(String iconName, String text, ActionListener listener, String tooltip) {
        JButton btn = new JButton();
        btn.setText(text);
        btn.setToolTipText(tooltip);
        btn.setIcon(WindowHelper.loadIcon('/' + iconName + ".png", 24, 24));
        btn.addActionListener(listener);
        btn.setBackground(Color.WHITE);
        return btn;
    }

    /** Creates the widgets shown in the content */
    private JPanel createContent() {
        JMenuItem verifyItem;
        JMenuItem reinstallItem;

        JPanel content = new JPanel();
        content.setBackground(Color.WHITE);
        content.setLayout(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(0, 10, 10, 10));

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

        TableColumn columnP = columnModel.getColumn(BrowserDialogTableColumn.PURPOSE.ordinal());
        columnP.setPreferredWidth(25);
        columnP.setCellRenderer(new BrowserDialogPurposeCellRenderer());

        TableColumn columnR = columnModel.getColumn(BrowserDialogTableColumn.REMOTE.ordinal());
        columnR.setPreferredWidth(150);

        TableColumn columnA = columnModel.getColumn(BrowserDialogTableColumn.AUTOSTART.ordinal());
        columnA.setPreferredWidth(30);
        columnA.setCellRenderer(new BrowserDialogAutostartCellRenderer(bhiveDir, auditor, sortModel));

        // Launch on double click
        table.addMouseListener(new DoubleClickListener());

        // Launch on enter key
        InputMap inputMap = table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "launch");
        table.getActionMap().put("launch", new EnterAction());

        // Sort and filter
        table.setRowSorter(sortModel);
        RowSorter.SortKey sortKey = new RowSorter.SortKey(BrowserDialogTableColumn.APP.ordinal(), SortOrder.ASCENDING);
        sortModel.setMaxSortKeys(1);
        sortModel.setSortKeys(Collections.singletonList(sortKey));

        // Context menu
        launchItem = new JMenuItem(launchButton.getText());
        launchItem.setIcon(WindowHelper.loadIcon("/launch.png", 16, 16));
        launchItem.setToolTipText(launchButton.getToolTipText());
        launchItem.addActionListener(this::onLaunchButtonClicked);

        customizeAndLaunchItem = new JMenuItem("Customize & Launch");
        customizeAndLaunchItem.setToolTipText("Opens a dialog to modify the application arguments before launching");
        customizeAndLaunchItem.setIcon(WindowHelper.loadIcon("/customizeAndLaunch.png", 16, 16));
        customizeAndLaunchItem.addActionListener(this::onLaunchButtonClicked);

        refreshItem = new JMenuItem(refreshButton.getText());
        refreshItem.setIcon(WindowHelper.loadIcon("/refresh.png", 16, 16));
        refreshItem.setToolTipText(refreshButton.getToolTipText());
        refreshItem.addActionListener(this::onRefreshButtonClicked);

        updateItem = new JMenuItem("Update");
        updateItem.setIcon(WindowHelper.loadIcon("/update.png", 16, 16));
        updateItem.setToolTipText("Installs the latest available version of the selected application");
        updateItem.addActionListener(this::onUpdateButtonClicked);

        uninstallItem = new JMenuItem(uninstallButton.getText());
        uninstallItem.setIcon(WindowHelper.loadIcon("/uninstall.png", 16, 16));
        uninstallItem.setToolTipText(uninstallButton.getToolTipText());
        uninstallItem.addActionListener(this::onUninstallButtonClicked);

        verifyItem = new JMenuItem(verifyButton.getText());
        verifyItem.setIcon(WindowHelper.loadIcon("/verify.png", 16, 16));
        verifyItem.setToolTipText(verifyButton.getToolTipText());
        verifyItem.addActionListener(this::onVerifyButtonClicked);

        reinstallItem = new JMenuItem(reinstallButton.getText());
        reinstallItem.setIcon(WindowHelper.loadIcon("/reinstall.png", 16, 16));
        reinstallItem.setToolTipText(reinstallButton.getToolTipText());
        reinstallItem.addActionListener(this::onReinstallButtonClicked);

        JPopupMenu menu = new JPopupMenu();
        menu.add(launchItem);
        menu.add(customizeAndLaunchItem);
        menu.add(new JSeparator());
        menu.add(refreshItem);
        menu.add(updateItem);
        menu.add(new JSeparator());
        menu.add(uninstallItem);
        menu.add(new JSeparator());
        menu.add(verifyItem);
        menu.add(reinstallItem);
        table.setComponentPopupMenu(menu);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBackground(Color.WHITE);
        scrollPane.setBorder(new EmptyBorder(10, 0, 0, 0));
        content.add(scrollPane, BorderLayout.CENTER);

        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        progressBar.setStringPainted(true);
        content.add(progressBar, BorderLayout.SOUTH);

        return content;
    }

    /** Creates the widgets shown in the footer */
    private JPanel createFooter() {
        JPanel footer = new JPanel();
        footer.setBorder(new EmptyBorder(0, 10, 10, 10));
        footer.setLayout(new BorderLayout(15, 15));

        JLabel home = new JLabel(
                "<HTML><U>" + rootDir.toAbsolutePath().toString() + "</U>" + (readonlyRoot ? (" (readonly)") : "") + "</HTML>");
        home.setToolTipText("Open home directory");
        home.setHorizontalAlignment(SwingConstants.LEFT);
        home.setOpaque(false);
        home.setBackground(Color.WHITE);
        home.addMouseListener(new OpenHomeFolder());
        footer.add(home, BorderLayout.WEST);

        JLabel version = new JLabel("Launcher version: " + VersionHelper.getVersion().toString());
        footer.add(version, BorderLayout.EAST);

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
        List<String> args = new ArrayList<>();
        if (e.getSource() == customizeAndLaunchItem) {
            args.add("--customizeArgs");
        }
        doLaunch(app, args);
    }

    /** Notification that the selected app should be updated */
    private void onUpdateButtonClicked(ActionEvent e) {
        ClientSoftwareConfiguration app = getSelectedApps().get(0);
        List<String> args = new ArrayList<>();
        args.add("--updateOnly");

        progressBar.setIndeterminate(true);
        progressBar.setString("Updating '" + app.clickAndStart.applicationId + "'");

        AppUpdater task = new AppUpdater(rootDir, app, args);
        task.addPropertyChangeListener(this::doUpdateProgessBar);
        task.execute();
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
        List<ClientSoftwareConfiguration> apps = getSelectedApps();

        // Refresh and remember which apps have been added to the hive
        Map<String, ClientSoftwareConfiguration> oldAppMap = model.asMap();
        searchApps();
        Map<String, ClientSoftwareConfiguration> newAppMap = model.asMap();
        newAppMap.keySet().removeAll(oldAppMap.keySet());

        // If nothing is selected we refresh all apps
        // If there is something selected we refresh the selection
        // AND all apps that have been added to the hive
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

    /** Executes the prune operation on all local hives */
    private void onPruneButtonClicked(ActionEvent e) {
        try {
            List<Path> hives = ClientPathHelper.getHives(rootDir);

            progressBar.setIndeterminate(false);
            progressBar.setValue(0);
            progressBar.setMinimum(0);
            progressBar.setMaximum(hives.size());
            progressBar.setString("Pruning hives....");

            PruneTask task = new PruneTask(hives, auditor);
            task.addPropertyChangeListener(this::doUpdateProgessBar);
            task.execute();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Failed to prune local hives: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Executes the fix operation on all local hives */
    private void onFsckButtonClicked(ActionEvent e) {
        try {
            List<Path> hives = ClientPathHelper.getHives(rootDir);

            progressBar.setIndeterminate(false);
            progressBar.setValue(0);
            progressBar.setMinimum(0);
            progressBar.setMaximum(hives.size());
            progressBar.setString("Check manifest and object consistency....");

            FsckTask task = new FsckTask(hives, auditor);
            task.addPropertyChangeListener(this::doUpdateProgessBar);
            task.execute();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Failed to fix errors in local hives: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Executes the verify operation on a selected application */
    private void onVerifyButtonClicked(ActionEvent e) {
        try {
            ClientSoftwareConfiguration app = getSelectedApps().get(0);

            progressBar.setIndeterminate(true);
            progressBar.setString("Verifying '" + app.clickAndStart.applicationId + "'");

            List<Path> hives = ClientPathHelper.getHives(rootDir);
            VerifyTask task = new VerifyTask(hives, auditor, app.clickAndStart, rootDir);
            task.addPropertyChangeListener(this::doUpdateProgessBar);
            task.execute();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Failed to run verify operation: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Reinstall a selected application */
    private void onReinstallButtonClicked(ActionEvent e) {
        ClientSoftwareConfiguration app = getSelectedApps().get(0);
        String appName = app.metadata != null ? app.metadata.appName : app.clickAndStart.applicationId;

        String message = "Are you sure you want to reinstall '" + appName + "'?";
        int result = JOptionPane.showConfirmDialog(this, message, "Reinstall", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            doReinstall(app);
        }
    }

    /** Launches the given application */
    private void doLaunch(ClientSoftwareConfiguration app, List<String> args) {
        progressBar.setIndeterminate(true);
        progressBar.setString("Launching '" + app.clickAndStart.applicationId + "'");

        AppLauncher task = new AppLauncher(rootDir, app, args);
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

    /** Reinstalls the given application */
    private void doReinstall(ClientSoftwareConfiguration app) {
        progressBar.setIndeterminate(true);
        progressBar.setString("Reinstalling '" + app.clickAndStart.applicationId + "'");

        AppReinstaller task = new AppReinstaller(rootDir, app);
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

        AppRefresher task = new AppRefresher(rootDir, auditor, apps);
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
        if (e.getPropertyName().equals(PropertyChangeActivityReporter.ACTIVITY_NAME)) {
            progressBar.setString((String) e.getNewValue());
        }
        doUpdateButtonState();
    }

    /** Updates the enabled state of all buttons */
    private void doUpdateButtonState() {
        if (progressBar.isVisible()) {
            launchItem.setEnabled(false);
            customizeAndLaunchItem.setEnabled(false);
            updateItem.setEnabled(false);
            refreshButton.setEnabled(false);

            uninstallItem.setEnabled(false);
            uninstallButton.setEnabled(false);

            refreshItem.setEnabled(false);
            refreshButton.setEnabled(false);

            fsckButton.setEnabled(false);
            pruneButton.setEnabled(false);

            verifyButton.setEnabled(false);
            reinstallButton.setEnabled(false);
            return;
        }

        List<ClientSoftwareConfiguration> apps = getSelectedApps();
        launchButton.setEnabled(apps.size() == 1);
        launchItem.setEnabled(apps.size() == 1);

        uninstallItem.setEnabled(!readonlyRoot && apps.size() == 1);
        uninstallButton.setEnabled(!readonlyRoot && apps.size() == 1);

        refreshItem.setEnabled(!readonlyRoot);
        refreshButton.setEnabled(!readonlyRoot);

        // --customizeArgs and launch needs at version 3.3.0
        customizeAndLaunchItem.setEnabled(checkVersion(apps, new Version(3, 3, 0, null)));

        // --updateOnly flag needs at least version 3.6.5
        updateItem.setEnabled(!readonlyRoot && checkVersion(apps, new Version(3, 6, 5, null)));

        // Error fixing and pruning require write permissions
        if (!readonlyRoot) {
            fsckButton.setEnabled(true);
            pruneButton.setEnabled(true);
        }

        verifyButton.setEnabled(!readonlyRoot && apps.size() == 1);
        reinstallButton.setEnabled(!readonlyRoot && apps.size() == 1);
    }

    /** Returns if the selected applications have at least the given version */
    private boolean checkVersion(List<ClientSoftwareConfiguration> apps, Version minVersion) {
        for (ClientSoftwareConfiguration app : apps) {
            Key launcher = app.launcher;
            if (launcher == null) {
                continue;
            }
            Version version = VersionHelper.tryParse(launcher.getTag());
            if (version.compareTo(minVersion) < 0) {
                return false;
            }
        }
        return !apps.isEmpty();
    }

    private class OpenHomeFolder extends MouseAdapter {

        @Override
        public void mouseClicked(MouseEvent e) {
            try {
                Desktop.getDesktop().browse(rootDir.toUri());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(BrowserDialog.this, "Failed to open home directory: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
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
            doLaunch(apps.get(0), Collections.emptyList());
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
            doLaunch(apps.get(0), Collections.emptyList());
        }
    }
}
