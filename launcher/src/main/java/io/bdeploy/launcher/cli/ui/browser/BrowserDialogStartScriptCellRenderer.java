package io.bdeploy.launcher.cli.ui.browser;

import java.awt.Color;
import java.awt.Component;
import java.net.URI;
import java.nio.file.Path;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.launcher.LocalClientApplicationSettings;
import io.bdeploy.launcher.LocalClientApplicationSettings.StartScriptInfo;
import io.bdeploy.launcher.LocalClientApplicationSettingsManifest;
import io.bdeploy.launcher.cli.ClientApplicationDto;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;
import io.bdeploy.logging.audit.RollingFileAuditor;

class BrowserDialogStartScriptCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    private final URI bhiveDir;
    private final Auditor auditor;
    private final TableRowSorter<BrowserDialogTableModel> sortModel;

    public BrowserDialogStartScriptCellRenderer(Path bhiveDir, Auditor auditor,
            TableRowSorter<BrowserDialogTableModel> sortModel) {
        this.bhiveDir = bhiveDir.toUri();
        this.auditor = auditor != null ? auditor : RollingFileAuditor.getFactory().apply(bhiveDir);
        this.sortModel = sortModel;
    }

    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
        super.getTableCellRendererComponent(t, v, s, f, r, c);
        if (s) {
            return this;
        }

        Color backgroundColor;
        if (t.getModel() instanceof BrowserDialogTableModel bdTableModel) {
            ClientSoftwareConfiguration config = bdTableModel.get(sortModel.convertRowIndexToModel(r));
            ClientApplicationDto metadata = config.metadata;
            if (metadata == null) {
                backgroundColor = BrowserDialogTableColorConstants.COULD_NOT_CALCULATE;
            } else {
                LocalClientApplicationSettings settings = null;
                try (BHive hive = new BHive(bhiveDir, auditor, new ActivityReporter.Null())) {
                    settings = new LocalClientApplicationSettingsManifest(hive).read();
                }
                if (settings == null) {
                    backgroundColor = BrowserDialogTableColorConstants.COULD_NOT_CALCULATE;
                } else {
                    StartScriptInfo startScriptInfo = settings.getStartScriptInfo(metadata.startScriptName);
                    backgroundColor = startScriptInfo == null//
                            ? BrowserDialogTableColorConstants.DISABLED
                            : config.clickAndStart.equals(startScriptInfo.getDescriptor())//
                                    ? BrowserDialogTableColorConstants.ENABLED//
                                    : BrowserDialogTableColorConstants.PAY_ATTENTION;
                }
            }
        } else {
            backgroundColor = BrowserDialogTableColorConstants.COULD_NOT_CALCULATE;
        }

        setBackground(backgroundColor);
        return this;
    }
}
