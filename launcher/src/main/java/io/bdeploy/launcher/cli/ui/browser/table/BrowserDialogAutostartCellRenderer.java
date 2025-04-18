package io.bdeploy.launcher.cli.ui.browser.table;

import java.awt.Component;
import java.net.URI;
import java.nio.file.Path;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.swing.JCheckBox;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
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

public class BrowserDialogAutostartCellRenderer extends JCheckBox implements TableCellRenderer {

    private static final long serialVersionUID = 1L;
    private static final Border noFocusBorder = new EmptyBorder(1, 1, 1, 1);

    private final URI bhiveDir;
    private final Auditor auditor;
    private final TableRowSorter<BrowserDialogTableModel> sortModel;

    public BrowserDialogAutostartCellRenderer(Path bhiveDir, Auditor auditor, TableRowSorter<BrowserDialogTableModel> sortModel) {
        super();
        this.bhiveDir = bhiveDir.toUri();
        this.auditor = auditor != null ? auditor : RollingFileAuditor.getFactory().apply(bhiveDir);
        this.sortModel = sortModel;
        setHorizontalAlignment(SwingConstants.CENTER);
        setBorderPainted(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
        setBorder(f ? UIManager.getBorder("Table.focusCellHighlightBorder") : noFocusBorder);
        setSelected((v != null && ((Boolean) v).booleanValue()));

        if (s) {
            setForeground(t.getSelectionForeground());
            setBackground(t.getSelectionBackground());
            return this;
        }

        setForeground(t.getForeground());

        if (t.getModel() instanceof BrowserDialogTableModel bdTableModel) {
            ClientSoftwareConfiguration config = bdTableModel.get(sortModel.convertRowIndexToModel(r));
            ClientApplicationDto metadata = config.metadata;
            if (metadata == null) {
                setBackground(BrowserDialogTableCellColorConstants.COULD_NOT_CALCULATE);
            } else if (metadata.supportsAutostart) {
                LocalClientApplicationSettings settings;
                try (BHive hive = new BHive(bhiveDir, auditor, new ActivityReporter.Null())) {
                    settings = new LocalClientApplicationSettingsManifest(hive).read();
                }
                Boolean autostartEnabled = settings.getAutostartEnabled(config.clickAndStart);
                setBackground(autostartEnabled != null && autostartEnabled != metadata.autostart
                        ? BrowserDialogTableCellColorConstants.PAY_ATTENTION
                        : t.getBackground());
            } else {
                setBackground(BrowserDialogTableCellColorConstants.DISABLED);
            }
        } else {
            setBackground(BrowserDialogTableCellColorConstants.COULD_NOT_CALCULATE);
        }

        return this;
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleBooleanRenderer();
        }
        return accessibleContext;
    }

    private class AccessibleBooleanRenderer extends JCheckBox.AccessibleJCheckBox {

        private static final long serialVersionUID = 1L;

        @Override
        public AccessibleAction getAccessibleAction() {
            return null;
        }
    }
}
