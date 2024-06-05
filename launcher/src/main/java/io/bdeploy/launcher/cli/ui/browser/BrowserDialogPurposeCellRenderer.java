package io.bdeploy.launcher.cli.ui.browser;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;

class BrowserDialogPurposeCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
        super.getTableCellRendererComponent(t, v, s, f, r, c);
        if (s) {
            return this;
        }

        if (v instanceof InstancePurpose purpose) {
            switch (purpose) {
                case DEVELOPMENT:
                    setBackground(BrowserDialogTableColorConstants.PURPOSE_DEVELOPMENT);
                    return this;
                case TEST:
                    setBackground(BrowserDialogTableColorConstants.PURPOSE_TEST);
                    return this;
                case PRODUCTIVE:
                    setBackground(BrowserDialogTableColorConstants.PURPOSE_PRODUCTIVE);
                    return this;
            }
        }

        setBackground(BrowserDialogTableColorConstants.COULD_NOT_CALCULATE);
        return this;
    }
}
