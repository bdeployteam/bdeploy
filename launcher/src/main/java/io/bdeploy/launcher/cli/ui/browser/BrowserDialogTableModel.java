package io.bdeploy.launcher.cli.ui.browser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.table.AbstractTableModel;

import io.bdeploy.launcher.cli.ClientApplicationDto;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;

class BrowserDialogTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    public static final int COL_APP = 0;
    public static final int COL_IG = 1;
    public static final int COL_INSTANCE = 2;
    public static final int COL_PURPOSE = 3;
    public static final int COL_PRODUCT = 4;
    public static final int COL_REMOTE = 5;

    private final List<ClientSoftwareConfiguration> apps = new ArrayList<>();

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
        return 6;
    }

    @Override
    public String getColumnName(int columnIndex) {
        switch (columnIndex) {
            case COL_APP:
                return "Application";
            case COL_IG:
                return "Instance Group";
            case COL_INSTANCE:
                return "Instance";
            case COL_PURPOSE:
                return "Purpose";
            case COL_PRODUCT:
                return "Product";
            case COL_REMOTE:
                return "Remote";
            default:
                return null;
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == COL_PURPOSE) {
            return Enum.class;
        }
        return String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ClientSoftwareConfiguration app = apps.get(rowIndex);
        ClientApplicationDto metadata = app.metadata;
        switch (columnIndex) {
            case COL_APP:
                if (metadata == null) {
                    return app.clickAndStart.applicationId;
                }
                return metadata.appName;
            case COL_INSTANCE:
                if (metadata == null) {
                    return app.clickAndStart.instanceId;
                }
                return metadata.instanceName;
            case COL_IG:
                if (metadata == null) {
                    return app.clickAndStart.groupId;
                }
                return metadata.instanceGroupTitle;
            case COL_PURPOSE:
                if (metadata == null) {
                    return "N/A";
                }
                return metadata.purpose;
            case COL_PRODUCT:
                if (metadata == null) {
                    return "N/A";
                }
                return metadata.product.getTag();
            case COL_REMOTE:
                return app.clickAndStart.host.getUri().toString();
            default:
                return null;
        }
    }

}