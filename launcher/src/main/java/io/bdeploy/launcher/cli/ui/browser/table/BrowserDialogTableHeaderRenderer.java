package io.bdeploy.launcher.cli.ui.browser.table;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

import io.bdeploy.launcher.cli.ui.WindowHelper;

public class BrowserDialogTableHeaderRenderer extends JLabel implements TableCellRenderer {

    private static final long serialVersionUID = 1L;

    public BrowserDialogTableHeaderRenderer() {
        setOpaque(true);
        setFont(getFont().deriveFont(Font.BOLD));
        setHorizontalAlignment(SwingConstants.CENTER);
        setBorder(BorderFactory.createLineBorder(new Color(128, 128, 128)));
    }

    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int rowIdx, int colIdx) {

        // Apply default size
        TableColumn column = t.getColumnModel().getColumn(colIdx);
        int preferredWidth = column.getPreferredWidth();
        setPreferredSize(new Dimension(preferredWidth, 30));

        // Apply value
        if (v != null) {
            setText(v.toString());
        }

        // Apply tooltip
        if (t.getModel() instanceof BrowserDialogTableModel bdTableModel) {
            setToolTipText(bdTableModel.getColumnHint(colIdx));
        }

        // Apply sort icon
        SortOrder sortOrder = getSortOrder(t, colIdx);
        switch (sortOrder) {
            case ASCENDING:
                setIcon(WindowHelper.loadSvgIcon("arrow_up"));
                break;
            case DESCENDING:
                setIcon(WindowHelper.loadSvgIcon("arrow_down"));
                break;
            case UNSORTED:
                setIcon(null);
                break;
        }

        return this;
    }

    /** Returns the sort order for the given column */
    private static SortOrder getSortOrder(JTable t, int colIdx) {
        RowSorter<? extends TableModel> sorter = t.getRowSorter();
        for (SortKey key : sorter.getSortKeys()) {
            if (key.getColumn() == colIdx) {
                return key.getSortOrder();
            }
        }
        return SortOrder.UNSORTED;
    }
}
