package io.bdeploy.launcher.cli.ui.browser.table;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;

import io.bdeploy.bhive.BHive;
import io.bdeploy.launcher.LocalClientApplicationSettings;
import io.bdeploy.launcher.LocalClientApplicationSettingsManifest;
import io.bdeploy.launcher.cli.ClientApplicationDto;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;

public class BrowserDialogTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private final List<ClientSoftwareConfiguration> apps = new ArrayList<>();
    private final BHive bhive;
    private LocalClientApplicationSettings settings;

    public BrowserDialogTableModel(BHive hive) {
        this.bhive = hive;
        addTableModelListener(new TableModelListener() {

            @Override
            public void tableChanged(TableModelEvent e) {
                settings = new LocalClientApplicationSettingsManifest(bhive).read();
            }
        });
    }

    /**
     * Returns the current snapshot of the {@link LocalClientApplicationSettings}.
     */
    public LocalClientApplicationSettings getCurrentSettings() {
        return settings;
    }

    /**
     * Appends all applications to the list of applications.
     */
    public void addAll(Collection<ClientSoftwareConfiguration> apps) {
        int oldSize = this.apps.size();
        this.apps.addAll(apps);
        if (!apps.isEmpty()) {
            fireTableRowsInserted(oldSize, apps.size() - 1);
        }
    }

    /**
     * Returns the application at the specified index
     */
    public ClientSoftwareConfiguration get(int row) {
        return this.apps.get(row);
    }

    /**
     * Returns a read-only copy of all applications.
     */
    public List<ClientSoftwareConfiguration> getAll() {
        return Collections.unmodifiableList(this.apps);
    }

    /**
     * Removes all available apps.
     */
    public void clear() {
        int oldSize = this.apps.size();
        this.apps.clear();
        if (oldSize > 0) {
            fireTableRowsDeleted(0, oldSize - 1);
        }
    }

    /**
     * Returns all applications indexed by their unique ID
     */
    public Map<String, ClientSoftwareConfiguration> asMap() {
        Map<String, ClientSoftwareConfiguration> map = new TreeMap<>();
        for (ClientSoftwareConfiguration app : apps) {
            map.put(app.clickAndStart.applicationId, app);
        }
        return map;
    }

    @Override
    public int getRowCount() {
        return apps.size();
    }

    @Override
    public int getColumnCount() {
        return BrowserDialogTableColumn.values().length;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return BrowserDialogTableColumn.fromIndex(columnIndex).columnClass;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return BrowserDialogTableColumn.fromIndex(columnIndex).columnName;
    }

    public String getColumnHint(int columnIndex) {
        return BrowserDialogTableColumn.fromIndex(columnIndex).columnHint;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        if (BrowserDialogTableColumn.fromIndex(columnIndex) != BrowserDialogTableColumn.AUTOSTART) {
            return false;
        }
        ClientApplicationDto metadata = apps.get(rowIndex).metadata;
        return metadata != null && metadata.supportsAutostart;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (BrowserDialogTableColumn.fromIndex(columnIndex) == BrowserDialogTableColumn.AUTOSTART) {
            LocalClientApplicationSettingsManifest manifest = new LocalClientApplicationSettingsManifest(bhive);
            settings.putAutostartEnabled(apps.get(rowIndex).clickAndStart, (boolean) aValue);
            manifest.write(settings);
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ClientSoftwareConfiguration app = apps.get(rowIndex);
        ClientApplicationDto metadata = app.metadata;
        switch (BrowserDialogTableColumn.fromIndex(columnIndex)) {
            case APP:
                return metadata != null ? metadata.appName : app.clickAndStart.applicationId;
            case INSTANCE:
                return metadata != null ? metadata.instanceName : app.clickAndStart.instanceId;
            case IG:
                return metadata != null ? metadata.instanceGroupTitle : app.clickAndStart.groupId;
            case PURPOSE:
                return metadata != null ? metadata.purpose : "N/A";
            case PRODUCT:
                return metadata != null ? metadata.product.getTag() : "N/A";
            case REMOTE:
                return app.clickAndStart.host.getUri().toString();
            case SERVER_VERSION:
                return metadata != null ? metadata.serverVersion : "N/A";
            case AUTOSTART:
                Boolean autostartEnabled = settings.getAutostartEnabled(apps.get(rowIndex).clickAndStart);
                if (autostartEnabled != null) {
                    return autostartEnabled;
                }
                return metadata != null && metadata.autostart;
            case START_SCRIPT:
                return metadata != null ? metadata.startScriptName : "";
            case FILE_ASSOC_EXTENSION:
                return metadata != null ? metadata.fileAssocExtension : "";
            case OFFLINE_LAUNCHABLE:
                return metadata != null ? metadata.offlineStartAllowed : null;
        }
        return null;
    }
}
