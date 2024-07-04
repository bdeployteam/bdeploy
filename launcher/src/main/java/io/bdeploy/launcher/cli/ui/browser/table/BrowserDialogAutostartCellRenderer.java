package io.bdeploy.launcher.cli.ui.browser.table;

import java.awt.Component;
import java.net.URI;
import java.nio.file.Path;

import javax.swing.JTable;
import javax.swing.plaf.UIResource;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.launcher.LocalClientApplicationSettings;
import io.bdeploy.launcher.LocalClientApplicationSettingsManifest;
import io.bdeploy.launcher.cli.ClientApplicationDto;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;
import io.bdeploy.logging.audit.RollingFileAuditor;

public class BrowserDialogAutostartCellRenderer implements TableCellRenderer, UIResource {

    private final URI bhiveDir;
    private final Auditor auditor;
    private final TableRowSorter<BrowserDialogTableModel> sortModel;

    public BrowserDialogAutostartCellRenderer(Path bhiveDir, Auditor auditor, TableRowSorter<BrowserDialogTableModel> sortModel) {
        this.bhiveDir = bhiveDir.toUri();
        this.auditor = auditor != null ? auditor : RollingFileAuditor.getFactory().apply(bhiveDir);
        this.sortModel = sortModel;
    }

    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
        Component component = t.getDefaultRenderer(Boolean.class).getTableCellRendererComponent(t, v, s, f, r, c);
        if (s) {
            return component;
        }

        if (t.getModel() instanceof BrowserDialogTableModel bdTableModel) {
            ClientSoftwareConfiguration config = bdTableModel.get(sortModel.convertRowIndexToModel(r));
            ClientApplicationDto metadata = config.metadata;
            if (metadata == null) {
                component.setBackground(BrowserDialogTableCellColorConstants.COULD_NOT_CALCULATE);
            } else if (metadata.supportsAutostart) {
                LocalClientApplicationSettings settings = null;
                try (BHive hive = new BHive(bhiveDir, auditor, new ActivityReporter.Null())) {
                    settings = new LocalClientApplicationSettingsManifest(hive).read();
                }
                if (settings != null) {
                    Boolean autostartEnabled = settings.getAutostartEnabled(config.clickAndStart);
                    if (autostartEnabled != null && autostartEnabled != metadata.autostart) {
                        component.setBackground(BrowserDialogTableCellColorConstants.PAY_ATTENTION);
                    }
                }
            } else {
                component.setBackground(BrowserDialogTableCellColorConstants.DISABLED);
            }
        } else {
            component.setBackground(BrowserDialogTableCellColorConstants.COULD_NOT_CALCULATE);
        }

        return component;
    }
}
