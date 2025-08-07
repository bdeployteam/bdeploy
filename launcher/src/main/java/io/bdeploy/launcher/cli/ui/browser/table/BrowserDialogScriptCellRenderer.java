package io.bdeploy.launcher.cli.ui.browser.table;

import java.awt.Component;
import java.awt.Font;
import java.util.function.BiFunction;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import io.bdeploy.launcher.LocalClientApplicationSettings;
import io.bdeploy.launcher.LocalClientApplicationSettings.ScriptInfo;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;

public class BrowserDialogScriptCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    private final TableRowSorter<BrowserDialogTableModel> sortModel;
    private final BiFunction<LocalClientApplicationSettings, String, ScriptInfo> scriptInfoExtractor;

    public BrowserDialogScriptCellRenderer(TableRowSorter<BrowserDialogTableModel> sortModel,
            BiFunction<LocalClientApplicationSettings, String, ScriptInfo> scriptInfoExtractor) {
        this.sortModel = sortModel;
        this.scriptInfoExtractor = scriptInfoExtractor;
    }

    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
        super.getTableCellRendererComponent(t, v, s, f, r, c);

        boolean isActive = false;
        if (t.getModel() instanceof BrowserDialogTableModel bdTableModel && v instanceof String currentValue) {
            ScriptInfo scriptInfo = scriptInfoExtractor.apply(bdTableModel.getCurrentSettings(), currentValue);
            ClientSoftwareConfiguration config = bdTableModel.get(sortModel.convertRowIndexToModel(r));
            isActive = scriptInfo != null && config.clickAndStart.equals(scriptInfo.getDescriptor());
        }

        setEnabled(isActive);
        if (isActive) {
            setFont(getFont().deriveFont(Font.BOLD));
            setText(getText() + " (active)");
        }

        return this;
    }
}
