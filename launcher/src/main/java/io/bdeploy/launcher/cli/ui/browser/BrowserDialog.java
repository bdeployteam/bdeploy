package io.bdeploy.launcher.cli.ui.browser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.KeyboardFocusManager;
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
import javax.swing.Box;
import javax.swing.BoxLayout;
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
import io.bdeploy.common.ActivityReporter;
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

    private JButton refreshAllButton;
    private JButton pruneButton;
    private JButton fsckButton;

    private JButton launchButton;
    private JButton customizeAndLaunchButton;
    private JButton refreshSelectedButton;
    private JButton activateStartScriptButton;
    private JButton activateFileAssocScriptButton;
    private JButton updateButton;
    private JButton verifyButton;
    private JButton reinstallButton;
    private JButton uninstallButton;

    private JMenuItem launchItem;
    private JMenuItem customizeAndLaunchItem;
    private JMenuItem refreshSelectedItem;
    private JMenuItem activateStartScriptItem;
    private JMenuItem activateFileAssocScriptItem;
    private JMenuItem updateItem;
    private JMenuItem verifyItem;
    private JMenuItem reinstallItem;
    private JMenuItem uninstallItem;

    private JProgressBar progressBar;

    public BrowserDialog(LauncherPathProvider lpp, Path userArea) {
        super(1400);

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

        // Initialize buttons and context menu items
        createButtonsAndItems();

        // Header area displaying a search field
        JPanel header = createHeader();
        add(header, BorderLayout.PAGE_START);

        // Content displaying the table
        JPanel content = createContent();
        add(content, BorderLayout.CENTER);

        // Footer displaying some progress
        JPanel footer = createFooter();
        add(footer, BorderLayout.PAGE_END);

        // Keyboard shortcuts
        KeyboardFocusManager keyManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        keyManager.addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_F5) {
                onRefreshAllEvent(null);
                return true;
            }
            return false;
        });

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

    private void createButtonsAndItems() {
        // Context insensitive buttons
        refreshAllButton = createJButton("refresh", "Refresh All", this::onRefreshAllEvent,//
                "Update the locally stored information (name, version...) of all applications");
        pruneButton = createJButton("prune", "Prune", this::onPruneEvent,//
                "Prune the selected application");
        fsckButton = createJButton("fixErrors", "Fix Errors", this::onFsckEvent,//
                "Fix any errors in the BHive");

        // Context sensitive buttons
        launchButton = createJButton("launch", "Launch", this::onLaunchEvent,//
                "Launch the selected application");
        customizeAndLaunchButton = createJButton("customizeAndLaunch", "Customize & Launch", this::onLaunchEvent,//
                "Open a dialog to modify the application arguments before launching");
        refreshSelectedButton = createJButton("refresh", "Refresh Selected", this::onRefreshSelectedEvent,//
                "Update the locally stored information (name, version...) of the selected application");
        activateStartScriptButton = createJButton("enable", "Activate Start Script", this::onActivateStartScriptEvent,//
                "Set the selected application to be started with its given script name");
        activateFileAssocScriptButton = createJButton("enable", "Activate File Association", this::onActivateFileAssocScriptEvent,//
                "Associate files with the selected application");
        updateButton = createJButton("update", "Update", this::onUpdateEvent,//
                "Install the latest available version of the selected application");
        verifyButton = createJButton("verify", "Verify", this::onVerifyEvent,//
                "Check if the selected application has missing or modified files");
        reinstallButton = createJButton("reinstall", "Reinstall", this::onReinstallEvent,//
                "Reinstall the selected application");
        uninstallButton = createJButton("uninstall", "Uninstall", this::onUninstallEvent,//
                "Uninstall the selected application");

        // Context menu items
        launchItem = createMenuItemFromButton(launchButton);
        customizeAndLaunchItem = createMenuItemFromButton(customizeAndLaunchButton);
        refreshSelectedItem = createMenuItemFromButton(refreshSelectedButton);
        activateStartScriptItem = createMenuItemFromButton(activateStartScriptButton);
        activateFileAssocScriptItem = createMenuItemFromButton(activateFileAssocScriptButton);
        updateItem = createMenuItemFromButton(updateButton);
        verifyItem = createMenuItemFromButton(verifyButton);
        reinstallItem = createMenuItemFromButton(reinstallButton);
        uninstallItem = createMenuItemFromButton(uninstallButton);
    }

    /** Creates the widgets shown in the header */
    private JPanel createHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.LINE_AXIS));
        header.setBorder(new EmptyBorder(5, 10, 0, 10));

        // Toolbar on the left side
        header.add(refreshAllButton);
        header.add(Box.createRigidArea(BUTTON_SEPARATOR_DIMENSION));
        header.add(pruneButton);
        header.add(Box.createRigidArea(BUTTON_SEPARATOR_DIMENSION));
        header.add(fsckButton);
        header.add(Box.createHorizontalGlue());

        // Search panel on the right side
        JPanel searchPanel = new JPanel();
        searchPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        header.add(searchPanel);

        JLabel searchLabel = new JLabel("Search:", SwingConstants.RIGHT);
        searchLabel.setOpaque(true);
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
    private JPanel createContent() {
        table.setShowHorizontalLines(true);
        table.setRowHeight(25);

        // Notify on selection changes
        ListSelectionModel selectionModel = table.getSelectionModel();
        selectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        selectionModel.addListSelectionListener(this::onSelectionChangedEvent);

        // Setup a nicer table header
        JTableHeader header = table.getTableHeader();
        header.setDefaultRenderer(new BrowserDialogTableHeaderRenderer());

        // Setup default column properties
        TableColumnModel columnModel = table.getColumnModel();

        TableColumn columnP = columnModel.getColumn(BrowserDialogTableColumn.PURPOSE.ordinal());
        columnP.setPreferredWidth(35);
        columnP.setCellRenderer(new BrowserDialogPurposeCellRenderer());

        TableColumn columnR = columnModel.getColumn(BrowserDialogTableColumn.REMOTE.ordinal());
        columnR.setPreferredWidth(150);

        TableColumn columnA = columnModel.getColumn(BrowserDialogTableColumn.AUTOSTART.ordinal());
        columnA.setPreferredWidth(10);
        columnA.setCellRenderer(new BrowserDialogAutostartCellRenderer(sortModel));

        TableColumn columnS = columnModel.getColumn(BrowserDialogTableColumn.START_SCRIPT.ordinal());
        columnS.setCellRenderer(new BrowserDialogScriptCellRenderer(bhiveDir, auditor, sortModel, (settings, metadata) -> settings
                .getStartScriptInfo(ScriptUtils.getStartScriptIdentifier(os, metadata.startScriptName))));

        TableColumn columnF = columnModel.getColumn(BrowserDialogTableColumn.FILE_ASSOC_EXTENSION.ordinal());
        columnF.setCellRenderer(new BrowserDialogScriptCellRenderer(bhiveDir, auditor, sortModel, (settings, metadata) -> settings
                .getFileAssocScriptInfo(ScriptUtils.getFileAssocIdentifier(os, metadata.fileAssocExtension))));

        TableColumn columnO = columnModel.getColumn(BrowserDialogTableColumn.OFFLINE_LAUNCHABLE.ordinal());
        columnO.setPreferredWidth(10);

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
        JPopupMenu menu = new JPopupMenu();
        menu.add(launchItem);
        menu.add(customizeAndLaunchItem);
        menu.add(refreshSelectedItem);
        menu.add(new JSeparator());
        menu.add(activateStartScriptItem);
        menu.add(activateFileAssocScriptItem);
        menu.add(new JSeparator());
        menu.add(updateItem);
        menu.add(verifyItem);
        menu.add(reinstallItem);
        menu.add(uninstallItem);
        table.setComponentPopupMenu(menu);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Progress bar
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        progressBar.setStringPainted(true);

        // Context sensitive toolbar
        Dimension toolbarSeparatorDimension = new Dimension(10, 0);
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.LINE_AXIS));
        toolbar.setBorder(new EmptyBorder(5, 10, 5, 10));
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(uninstallButton);
        toolbar.add(Box.createRigidArea(BUTTON_SEPARATOR_DIMENSION));
        toolbar.add(reinstallButton);
        toolbar.add(Box.createRigidArea(BUTTON_SEPARATOR_DIMENSION));
        toolbar.add(verifyButton);
        toolbar.add(Box.createRigidArea(BUTTON_SEPARATOR_DIMENSION));
        toolbar.add(updateButton);
        toolbar.add(Box.createRigidArea(toolbarSeparatorDimension));
        toolbar.add(activateFileAssocScriptButton);
        toolbar.add(Box.createRigidArea(BUTTON_SEPARATOR_DIMENSION));
        toolbar.add(activateStartScriptButton);
        toolbar.add(Box.createRigidArea(toolbarSeparatorDimension));
        toolbar.add(refreshSelectedButton);
        toolbar.add(Box.createRigidArea(BUTTON_SEPARATOR_DIMENSION));
        toolbar.add(customizeAndLaunchButton);
        toolbar.add(Box.createRigidArea(BUTTON_SEPARATOR_DIMENSION));
        toolbar.add(launchButton);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.PAGE_AXIS));
        content.add(scrollPane);
        content.add(progressBar);
        content.add(toolbar);

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
        home.setForeground(new Color(0, 0, 238));
        home.addMouseListener(new OpenHomeFolder());
        footer.add(home, BorderLayout.LINE_START);

        JLabel version = new JLabel("Launcher version: " + VersionHelper.getVersion().toString());
        footer.add(version, BorderLayout.LINE_END);

        return footer;
    }

    private static JButton createJButton(String iconName, String text, ActionListener listener, String tooltip) {
        JButton btn = new JButton(text);
        btn.setToolTipText(tooltip);
        btn.setIcon(WindowHelper.loadSvgIcon(iconName));
        btn.addActionListener(listener);
        return btn;
    }

    private static JMenuItem createMenuItemFromButton(JButton source) {
        JMenuItem menuItem = new JMenuItem(source.getText());
        menuItem.setToolTipText(source.getToolTipText());
        menuItem.setIcon(source.getIcon());
        for (ActionListener listener : source.getActionListeners()) {
            menuItem.addActionListener(listener);
        }
        return menuItem;
    }

    /** Notification that the search field has changed */
    private void onFilterChanged(String text) {
        sortModel.setRowFilter(new BrowserDialogTableFilter(text.trim().toLowerCase()));
    }

    /** Notification that all apps should be refreshed */
    private void onRefreshAllEvent(ActionEvent e) {
        searchApps();
        doRefresh(model.getAll());
    }

    /** Executes the prune operation on all local hives */
    private void onPruneEvent(ActionEvent e) {
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
    private void onFsckEvent(ActionEvent e) {
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
    private void onLaunchEvent(ActionEvent e) {
        ClientSoftwareConfiguration app = getSelectedApps().get(0);
        List<String> args = new ArrayList<>();
        Object source = e.getSource();
        if (source == customizeAndLaunchButton || source == customizeAndLaunchItem) {
            args.add("--customizeArgs");
        }
        doLaunch(app, args);
    }

    /** Notification that the selected apps should be refreshed */
    private void onRefreshSelectedEvent(ActionEvent e) {
        List<ClientSoftwareConfiguration> apps = getSelectedApps();

        // Refresh and remember which apps have been added to the hive
        Map<String, ClientSoftwareConfiguration> oldAppMap = model.asMap();
        searchApps();
        Map<String, ClientSoftwareConfiguration> newAppMap = model.asMap();
        newAppMap.keySet().removeAll(oldAppMap.keySet());

        // Refresh the selection and all apps that have been added to the hive
        apps.addAll(newAppMap.values());
        doRefresh(apps);
    }

    /** Activate the start script of the selected application */
    private void onActivateStartScriptEvent(ActionEvent e) {
        handleScriptChange(pathProvider -> new LocalStartScriptHelper(os, auditor, pathProvider), "start");
    }

    /** Activate the file association script of the selected application */
    private void onActivateFileAssocScriptEvent(ActionEvent e) {
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
        searchApps();
    }

    /** Notification that the selected app should be updated */
    private void onUpdateEvent(ActionEvent e) {
        ClientSoftwareConfiguration app = getSelectedApps().get(0);
        List<String> args = new ArrayList<>();
        args.add("--updateOnly");

        progressBar.setIndeterminate(true);
        progressBar.setString("Updating '" + app.clickAndStart.applicationId + "'");

        AppUpdater task = new AppUpdater(lpp, auditor, app, args);
        task.addPropertyChangeListener(this::doUpdateProgessBar);
        task.addPropertyChangeListener(this::doRefreshApps);
        task.execute();
    }

    /** Executes the verify operation on a selected application */
    private void onVerifyEvent(ActionEvent e) {
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
    private void onReinstallEvent(ActionEvent e) {
        ClientSoftwareConfiguration app = getSelectedApps().get(0);
        String appName = app.metadata != null ? app.metadata.appName : app.clickAndStart.applicationId;

        String message = "Are you sure you want to reinstall '" + appName + "'?";
        int result = JOptionPane.showConfirmDialog(this, message, "Reinstall", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            doReinstall(app);
        }
    }

    /** Notification that the selected app should be removed */
    private void onUninstallEvent(ActionEvent e) {
        doUninstall(getSelectedApps().get(0));
    }

    /** Notification that the selected rows have changed */
    private void onSelectionChangedEvent(ListSelectionEvent e) {
        doUpdateButtonState();
    }

    /** Launches the given application */
    private void doLaunch(ClientSoftwareConfiguration app, List<String> args) {
        progressBar.setIndeterminate(true);
        progressBar.setString("Launching '" + app.clickAndStart.applicationId + "'");

        AppLauncher task = new AppLauncher(lpp, auditor, app, args, !readonlyHome);
        task.addPropertyChangeListener(this::doUpdateProgessBar);
        task.addPropertyChangeListener(this::doRefreshApps);
        task.execute();
    }

    /** Refreshes the given applications */
    private void doRefresh(List<ClientSoftwareConfiguration> apps) {
        int size = apps.size();

        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setMinimum(0);
        progressBar.setMaximum(size);
        progressBar.setString("Refreshing " + size + " application" + (size == 1 ? "" : "s") + "...");

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
        if (PropertyChangeActivityReporter.ACTIVITY_NAME.equals(e.getPropertyName())) {
            progressBar.setString((String) e.getNewValue());
        }
        doUpdateButtonState();
    }

    /** Updates the enabled state of all buttons */
    private void doUpdateButtonState() {
        if (progressBar.isVisible()) {
            refreshAllButton.setEnabled(false);
            pruneButton.setEnabled(false);
            fsckButton.setEnabled(false);

            launchItem.setEnabled(false);
            launchButton.setEnabled(false);
            customizeAndLaunchItem.setEnabled(false);
            customizeAndLaunchButton.setEnabled(false);
            refreshSelectedItem.setEnabled(false);
            refreshSelectedButton.setEnabled(false);

            activateStartScriptItem.setEnabled(false);
            activateStartScriptButton.setEnabled(false);
            activateFileAssocScriptItem.setEnabled(false);
            activateFileAssocScriptButton.setEnabled(false);

            updateItem.setEnabled(false);
            updateButton.setEnabled(false);
            verifyItem.setEnabled(false);
            verifyButton.setEnabled(false);
            reinstallItem.setEnabled(false);
            reinstallButton.setEnabled(false);
            uninstallItem.setEnabled(false);
            uninstallButton.setEnabled(false);
            return;
        }

        List<ClientSoftwareConfiguration> selectedApps = getSelectedApps();
        boolean singleAppSelected = selectedApps.size() == 1;
        boolean writeAllowed = !readonlyHome;
        boolean writeAllowedAndSingleAppSelected = singleAppSelected && writeAllowed;

        refreshAllButton.setEnabled(writeAllowed);
        fsckButton.setEnabled(writeAllowed);
        pruneButton.setEnabled(writeAllowed);

        launchButton.setEnabled(singleAppSelected);
        launchItem.setEnabled(singleAppSelected);
        customizeAndLaunchButton.setEnabled(singleAppSelected);
        customizeAndLaunchItem.setEnabled(singleAppSelected);
        refreshSelectedButton.setEnabled(writeAllowedAndSingleAppSelected);
        refreshSelectedItem.setEnabled(writeAllowedAndSingleAppSelected);

        boolean activateStartScriptEnabled = false;
        boolean activateFileAssocScriptEnabled = false;
        if (singleAppSelected) {
            ClientSoftwareConfiguration singleApp = selectedApps.iterator().next();
            ClientApplicationDto singleAppMetadata = singleApp.metadata;
            if (singleAppMetadata != null) {
                ClickAndStartDescriptor clickAndStart = singleApp.clickAndStart;
                LocalClientApplicationSettings settings;
                try (BHive hive = new BHive(bhiveDir.toUri(), auditor, new ActivityReporter.Null())) {
                    settings = new LocalClientApplicationSettingsManifest(hive).read();
                }
                ScriptInfo startScriptInfo = settings
                        .getStartScriptInfo(ScriptUtils.getStartScriptIdentifier(os, singleAppMetadata.startScriptName));
                if (startScriptInfo != null && !clickAndStart.equals(startScriptInfo.getDescriptor())) {
                    activateStartScriptEnabled = true;
                }
                ScriptInfo fileAssocScriptInfo = settings
                        .getFileAssocScriptInfo(ScriptUtils.getFileAssocIdentifier(os, singleAppMetadata.fileAssocExtension));
                if (fileAssocScriptInfo != null && !clickAndStart.equals(fileAssocScriptInfo.getDescriptor())) {
                    activateFileAssocScriptEnabled = true;
                }
            }
        }
        activateStartScriptButton.setEnabled(activateStartScriptEnabled);
        activateStartScriptItem.setEnabled(activateStartScriptEnabled);
        activateFileAssocScriptButton.setEnabled(activateFileAssocScriptEnabled);
        activateFileAssocScriptItem.setEnabled(activateFileAssocScriptEnabled);

        verifyButton.setEnabled(writeAllowedAndSingleAppSelected);
        verifyItem.setEnabled(writeAllowedAndSingleAppSelected);
        reinstallButton.setEnabled(writeAllowedAndSingleAppSelected);
        reinstallItem.setEnabled(writeAllowedAndSingleAppSelected);
        updateButton.setEnabled(writeAllowedAndSingleAppSelected);
        updateItem.setEnabled(writeAllowedAndSingleAppSelected);
        uninstallButton.setEnabled(writeAllowedAndSingleAppSelected);
        uninstallItem.setEnabled(writeAllowedAndSingleAppSelected);
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
