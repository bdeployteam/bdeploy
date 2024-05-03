package io.bdeploy.launcher.cli.ui.browser;

import java.awt.Color;
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
        if (v == InstancePurpose.DEVELOPMENT) {
            setBackground(new Color(51, 170, 0));
        } else if (v == InstancePurpose.TEST) {
            setBackground(new Color(51, 170, 255));
        } else {
            setBackground(Color.WHITE);
        }
        return this;
    }
}
