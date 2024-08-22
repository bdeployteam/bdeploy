package io.bdeploy.launcher.cli.ui.browser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
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
import java.util.function.Function;

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
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.launcher.ClientPathHelper;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;
import io.bdeploy.launcher.LocalClientApplicationSettings;
import io.bdeploy.launcher.LocalClientApplicationSettings.ScriptInfo;
import io.bdeploy.launcher.LocalClientApplicationSettingsManifest;
import io.bdeploy.launcher.cli.ClientApplicationDto;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;
import io.bdeploy.launcher.cli.ClientSoftwareManifest;
import io.bdeploy.launcher.cli.scripts.LocalScriptHelper;
import io.bdeploy.launcher.cli.scripts.ScriptUtils;
import io.bdeploy.launcher.cli.scripts.impl.LocalFileAssocScriptHelper;
import io.bdeploy.launcher.cli.scripts.impl.LocalStartScriptHelper;
import io.bdeploy.launcher.cli.ui.BaseDialog;
import io.bdeploy.launcher.cli.ui.WindowHelper;
import io.bdeploy.launcher.cli.ui.browser.table.BrowserDialogAutostartCellRenderer;
import io.bdeploy.launcher.cli.ui.browser.table.BrowserDialogPurposeCellRenderer;
import io.bdeploy.launcher.cli.ui.browser.table.BrowserDialogScriptCellRenderer;
import io.bdeploy.launcher.cli.ui.browser.table.BrowserDialogTableColumn;
import io.bdeploy.launcher.cli.ui.browser.table.BrowserDialogTableFilter;
import io.bdeploy.launcher.cli.ui.browser.table.BrowserDialogTableHeaderRenderer;
import io.bdeploy.launcher.cli.ui.browser.table.BrowserDialogTableModel;
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

    private final transient LauncherPathProvider lpp;
    private final transient Path homeDir;
    private final transient Path bhiveDir;
    private final transient boolean readonlyHome;
    private final transient Auditor auditor;

    private final OperatingSystem os;
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
    private JMenuItem activateStartScriptItem;
    private JMenuItem activateFileAssocScriptItem;

    private JProgressBar progressBar;

    public BrowserDialog(LauncherPathProvider lpp, Path userArea) {
        super(new Dimension(1024, 768));

        this.lpp = lpp;
        this.homeDir = lpp.get(SpecialDirectory.HOME);
        this.bhiveDir = lpp.get(SpecialDirectory.BHIVE);
        this.readonlyHome = userArea != null;
        this.auditor = userArea != null ? RollingFileAuditor.getFactory().apply(userArea) : null;

        this.os = OsHelper.getRunningOs();
        this.model = new BrowserDialogTableModel(bhiveDir, auditor);
        this.sortModel = new TableRowSorter<>(model);
        this.table = new JTable(model);

        setTitle("BDeploy Launcher");

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
        header.setBorder(DEFAULT_EMPTY_BORDER);
        header.setLayout(new BorderLayout());

        launchButton = createHeaderButton("launch", "Launch", this::onLaunchButtonClicked,//
                "Launch the selected application");

        refreshButton = createHeaderButton("refresh", "Refresh", this::onRefreshButtonClicked,//
                "Update the locally stored information (name, version...) of the selected applications");

        uninstallButton = createHeaderButton("uninstall", "Uninstall", this::onUninstallButtonClicked,//
                "Uninstall the selected application");

        pruneButton = createHeaderButton("prune", "Prune", this::onPruneButtonClicked,//
                "Prune the selected application");

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
        columnA.setPreferredWidth(40);
        columnA.setCellRenderer(new BrowserDialogAutostartCellRenderer(bhiveDir, auditor, sortModel));

        TableColumn columnS = columnModel.getColumn(BrowserDialogTableColumn.START_SCRIPT.ordinal());
        columnS.setCellRenderer(new BrowserDialogScriptCellRenderer(bhiveDir, auditor, sortModel, (settings, metadata) -> settings
                .getStartScriptInfo(ScriptUtils.getStartScriptIdentifier(os, metadata.startScriptName))));

        TableColumn columnF = columnModel.getColumn(BrowserDialogTableColumn.FILE_ASSOC_EXTENSION.ordinal());
        columnF.setCellRenderer(new BrowserDialogScriptCellRenderer(bhiveDir, auditor, sortModel, (settings, metadata) -> settings
                .getFileAssocScriptInfo(ScriptUtils.getFileAssocIdentifier(os, metadata.fileAssocExtension))));

        // Add MouseAdapter
        table.addMouseListener(new MouseAdapter() {

            // Select single row on right-click
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < table.getRowCount()) {
                        table.setRowSelectionInterval(row, row);
                    } else {
                        table.clearSelection();
                    }
                }
            }

            // Launch on double click
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
        });

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

        activateStartScriptItem = new JMenuItem("Activate Start Script");
        activateStartScriptItem.setIcon(WindowHelper.loadIcon("/enable.png", 16, 16));
        activateStartScriptItem.setToolTipText("Set this application to be started with its given script name.");
        activateStartScriptItem.addActionListener(this::onActivateStartScriptButtonClicked);

        activateFileAssocScriptItem = new JMenuItem("Activate File Association");
        activateFileAssocScriptItem.setIcon(WindowHelper.loadIcon("/enable.png", 16, 16));
        activateFileAssocScriptItem.setToolTipText("Associate files with this application.");
        activateFileAssocScriptItem.addActionListener(this::onActivateFileAssocScriptButtonClicked);

        JMenuItem verifyItem = new JMenuItem(verifyButton.getText());
        verifyItem.setIcon(WindowHelper.loadIcon("/verify.png", 16, 16));
        verifyItem.setToolTipText(verifyButton.getToolTipText());
        verifyItem.addActionListener(this::onVerifyButtonClicked);

        JMenuItem reinstallItem = new JMenuItem(reinstallButton.getText());
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
        menu.add(activateStartScriptItem);
        menu.add(activateFileAssocScriptItem);
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

        JLabel home = new JLabel("<HTML><U>" + homeDir.toString() + "</U>" + (readonlyHome ? (" (readonly)") : "") + "</HTML>");
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

    /** Notification that the selected app should be removed */
    private void onUninstallButtonClicked(ActionEvent e) {
        ClientSoftwareConfiguration app = getSelectedApps().get(0);
        doUninstall(app);
    }

    /** Executes the prune operation on all local hives */
    private void onPruneButtonClicked(ActionEvent e) {
        try {
            List<Path> hives = ClientPathHelper.getHives(lpp);

            progressBar.setIndeterminate(false);
            progressBar.setValue(0);
            progressBar.setMinimum(0);
            progressBar.setMaximum(hives.size());
            progressBar.setString("Pruning hives....");

            PruneTask task = new PruneTask(hives, auditor);
            task.addPropertyChangeListener(this::doUpdateProgessBar);
            task.execute();
        } catch (IOException ex) {
            showErrorMessageDialog(null, "Failed to prune local hives: " + ex.getMessage());
        }
    }

    /** Executes the fix operation on all local hives */
    private void onFsckButtonClicked(ActionEvent e) {
        try {
            List<Path> hives = ClientPathHelper.getHives(lpp);

            progressBar.setIndeterminate(false);
            progressBar.setValue(0);
            progressBar.setMinimum(0);
            progressBar.setMaximum(hives.size());
            progressBar.setString("Check manifest and object consistency....");

            FsckTask task = new FsckTask(hives, auditor);
            task.addPropertyChangeListener(this::doUpdateProgessBar);
            task.execute();
        } catch (IOException ex) {
            showErrorMessageDialog(null, "Failed to fix errors in local hives: " + ex.getMessage());
        }
    }

    /** Notification that the selected app should be updated */
    private void onUpdateButtonClicked(ActionEvent e) {
        ClientSoftwareConfiguration app = getSelectedApps().get(0);
        List<String> args = new ArrayList<>();
        args.add("--updateOnly");

        progressBar.setIndeterminate(true);
        progressBar.setString("Updating '" + app.clickAndStart.applicationId + "'");

        AppUpdater task = new AppUpdater(lpp, app, args);
        task.addPropertyChangeListener(this::doUpdateProgessBar);
        task.execute();
    }

    /** Executes the verify operation on a selected application */
    private void onVerifyButtonClicked(ActionEvent e) {
        try {
            ClientSoftwareConfiguration app = getSelectedApps().get(0);

            progressBar.setIndeterminate(true);
            progressBar.setString("Verifying '" + app.clickAndStart.applicationId + "'");

            VerifyTask task = new VerifyTask(lpp, auditor, app.clickAndStart);
            task.addPropertyChangeListener(this::doUpdateProgessBar);
            task.execute();
        } catch (Exception ex) {
            showErrorMessageDialog(null, "Failed to run verify operation: " + ex.getMessage());
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

    /** Activate the start script of the selected application */
    private void onActivateStartScriptButtonClicked(ActionEvent e) {
        handleScriptChange(pathProvider -> new LocalStartScriptHelper(os, auditor, pathProvider), "start");
    }

    /** Activate the file association script of the selected application */
    private void onActivateFileAssocScriptButtonClicked(ActionEvent e) {
        handleScriptChange(pathProvider -> new LocalFileAssocScriptHelper(os, auditor, pathProvider), "file association");
    }

    private void handleScriptChange(Function<LauncherPathProvider, LocalScriptHelper> scriptHelperCreator, String scriptType) {
        ClientSoftwareConfiguration config = getSelectedApps().get(0);
        ClickAndStartDescriptor clickAndStart = config.clickAndStart;
        LauncherPathProvider pathProvider = new LauncherPathProvider(homeDir).setApplicationId(clickAndStart.applicationId);
        try {
            scriptHelperCreator.apply(pathProvider).createScript(config.clientAppCfg, clickAndStart, true);
        } catch (IOException ex) {
            showErrorMessageDialog(null, "Failed to change active " + scriptType + " script: " + ex.getMessage());
        }
        doRefresh(model.getAll());
    }

    /** Notification that the selected rows have changed */
    private void onSelectionChanged(ListSelectionEvent e) {
        doUpdateButtonState();
    }

    /** Launches the given application */
    private void doLaunch(ClientSoftwareConfiguration app, List<String> args) {
        progressBar.setIndeterminate(true);
        progressBar.setString("Launching '" + app.clickAndStart.applicationId + "'");

        AppLauncher task = new AppLauncher(lpp, app, args);
        task.addPropertyChangeListener(this::doUpdateProgessBar);
        task.execute();
    }

    /** Refreshes the given applications */
    private void doRefresh(List<ClientSoftwareConfiguration> apps) {
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setMinimum(0);
        progressBar.setMaximum(apps.size());
        progressBar.setString("Refreshing applications...");

        AppRefresher task = new AppRefresher(lpp, auditor, apps);
        task.addPropertyChangeListener(this::doUpdateProgessBar);
        task.addPropertyChangeListener(this::doRefreshApps);
        task.execute();
    }

    /** Removes the given application */
    private void doUninstall(ClientSoftwareConfiguration app) {
        progressBar.setIndeterminate(true);
        progressBar.setString("Uninstalling '" + app.clickAndStart.applicationId + "'");

        AppUninstaller task = new AppUninstaller(lpp, app);
        task.addPropertyChangeListener(this::doUpdateProgessBar);
        task.addPropertyChangeListener(this::doRefreshApps);
        task.execute();
    }

    /** Reinstalls the given application */
    private void doReinstall(ClientSoftwareConfiguration app) {
        progressBar.setIndeterminate(true);
        progressBar.setString("Reinstalling '" + app.clickAndStart.applicationId + "'");

        AppReinstaller task = new AppReinstaller(lpp, app);
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
        if ("progress".equals(e.getPropertyName())) {
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

            activateStartScriptItem.setEnabled(false);

            refreshItem.setEnabled(false);
            refreshButton.setEnabled(false);

            fsckButton.setEnabled(false);
            pruneButton.setEnabled(false);

            verifyButton.setEnabled(false);
            reinstallButton.setEnabled(false);
            return;
        }

        List<ClientSoftwareConfiguration> selectedApps = getSelectedApps();
        boolean singleAppSelected = selectedApps.size() == 1;

        launchButton.setEnabled(singleAppSelected);
        launchItem.setEnabled(singleAppSelected);

        uninstallItem.setEnabled(!readonlyHome && singleAppSelected);
        uninstallButton.setEnabled(!readonlyHome && singleAppSelected);

        boolean activateStartScriptItemEnabled = false;
        boolean activateFileAssocScriptItemEnabled = false;
        if (singleAppSelected) {
            ClientSoftwareConfiguration singleApp = selectedApps.iterator().next();
            ClientApplicationDto singleAppMetadata = singleApp.metadata;
            if (singleAppMetadata != null) {
                LocalClientApplicationSettings settings;
                try (BHive hive = new BHive(bhiveDir.toUri(), auditor, new ActivityReporter.Null())) {
                    settings = new LocalClientApplicationSettingsManifest(hive).read();
                }

                ClickAndStartDescriptor clickAndStart = singleApp.clickAndStart;

                ScriptInfo startScriptInfo = settings.getStartScriptInfo(//
                        ScriptUtils.getStartScriptIdentifier(os, singleAppMetadata.startScriptName));
                if (startScriptInfo != null && !clickAndStart.equals(startScriptInfo.getDescriptor())) {
                    activateStartScriptItemEnabled = true;
                }
                ScriptInfo fileAssocScriptInfo = settings.getFileAssocScriptInfo(//
                        ScriptUtils.getFileAssocIdentifier(os, singleAppMetadata.fileAssocExtension));
                if (fileAssocScriptInfo != null && !clickAndStart.equals(fileAssocScriptInfo.getDescriptor())) {
                    activateFileAssocScriptItemEnabled = true;
                }
            }
        }
        activateStartScriptItem.setEnabled(activateStartScriptItemEnabled);
        activateFileAssocScriptItem.setEnabled(activateFileAssocScriptItemEnabled);

        refreshItem.setEnabled(!readonlyHome);
        refreshButton.setEnabled(!readonlyHome);

        // --customizeArgs and launch needs at version 3.3.0
        customizeAndLaunchItem.setEnabled(checkVersion(selectedApps, new Version(3, 3, 0, null)));

        // --updateOnly flag needs at least version 3.6.5
        updateItem.setEnabled(!readonlyHome && checkVersion(selectedApps, new Version(3, 6, 5, null)));

        // Error fixing and pruning require write permissions
        if (!readonlyHome) {
            fsckButton.setEnabled(true);
            pruneButton.setEnabled(true);
        }

        verifyButton.setEnabled(!readonlyHome && singleAppSelected);
        reinstallButton.setEnabled(!readonlyHome && singleAppSelected);
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

    private static void showErrorMessageDialog(Component parent, String text) {
        JOptionPane.showMessageDialog(parent, text, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private class OpenHomeFolder extends MouseAdapter {

        @Override
        public void mouseClicked(MouseEvent e) {
            try {
                Desktop.getDesktop().browse(homeDir.toUri());
            } catch (IOException ex) {
                showErrorMessageDialog(BrowserDialog.this, "Failed to open home directory: " + ex.getMessage());
            }
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
