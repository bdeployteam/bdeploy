package io.bdeploy.launcher.cli.ui.browser.table;

import java.awt.Component;
import java.awt.Font;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.BiFunction;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.launcher.LocalClientApplicationSettings;
import io.bdeploy.launcher.LocalClientApplicationSettings.ScriptInfo;
import io.bdeploy.launcher.LocalClientApplicationSettingsManifest;
import io.bdeploy.launcher.cli.ClientApplicationDto;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;
import io.bdeploy.logging.audit.RollingFileAuditor;

public class BrowserDialogScriptCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    private final URI bhiveDir;
    private final Auditor auditor;
    private final TableRowSorter<BrowserDialogTableModel> sortModel;
    private final BiFunction<LocalClientApplicationSettings, ClientApplicationDto, ScriptInfo> scriptInfoExtractor;

    public BrowserDialogScriptCellRenderer(Path bhiveDir, Auditor auditor, TableRowSorter<BrowserDialogTableModel> sortModel,
            BiFunction<LocalClientApplicationSettings, ClientApplicationDto, ScriptInfo> scriptInfoExtractor) {
        this.bhiveDir = bhiveDir.toUri();
        this.auditor = auditor != null ? auditor : RollingFileAuditor.getFactory().apply(bhiveDir);
        this.sortModel = sortModel;
        this.scriptInfoExtractor = scriptInfoExtractor;
    }

    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
        super.getTableCellRendererComponent(t, v, s, f, r, c);

        boolean isActive = false;
        if (t.getModel() instanceof BrowserDialogTableModel bdTableModel) {
            ClientSoftwareConfiguration config = bdTableModel.get(sortModel.convertRowIndexToModel(r));
            ClientApplicationDto metadata = config.metadata;
            if (metadata != null) {
                LocalClientApplicationSettings settings;
                try (BHive hive = new BHive(bhiveDir, auditor, new ActivityReporter.Null())) {
                    settings = new LocalClientApplicationSettingsManifest(hive).read();
                }
                ScriptInfo scriptInfo = scriptInfoExtractor.apply(settings, metadata);
                isActive = scriptInfo != null && config.clickAndStart.equals(scriptInfo.getDescriptor());
            }
        }

        setEnabled(isActive);
        if (isActive) {
            setFont(getFont().deriveFont(Font.BOLD));
            setText(getText() + " (active)");
        }

        return this;
    }
}
