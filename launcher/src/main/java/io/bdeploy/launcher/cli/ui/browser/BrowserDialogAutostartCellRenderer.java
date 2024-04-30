package io.bdeploy.launcher.cli.ui.browser;

import java.awt.Color;
import java.awt.Component;
import java.nio.file.Path;

import javax.swing.JTable;
import javax.swing.plaf.UIResource;
import javax.swing.table.TableCellRenderer;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.launcher.cli.ClientApplicationDto;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;
import io.bdeploy.logging.audit.RollingFileAuditor;

class BrowserDialogAutostartCellRenderer implements TableCellRenderer, UIResource {

    private final Path bhiveDir;
    private final Auditor auditor;

    public BrowserDialogAutostartCellRenderer(Path bhiveDir, Auditor auditor) {
        this.bhiveDir = bhiveDir;
        this.auditor = auditor != null ? auditor : RollingFileAuditor.getFactory().apply(bhiveDir);
    }

    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
        Component component = t.getDefaultRenderer(Boolean.class).getTableCellRendererComponent(t, v, s, f, r, c);

        if (!s && t.getModel() instanceof BrowserDialogTableModel bdTableModel) {
            ClientSoftwareConfiguration config = bdTableModel.get(r);
            ClientApplicationDto metadata = config.metadata;
            if (metadata != null && metadata.supportsAutostart) {
                Boolean storedValue = null;
                try (BHive hive = new BHive(bhiveDir.toUri(), auditor, new ActivityReporter.Null())) {
                    storedValue = new MetaManifest<>(config.key, false, Boolean.class).read(hive);
                }
                if (storedValue != null) {
                    if (storedValue == metadata.autostart) {
                        component.setBackground(Color.WHITE);
                    } else {
                        component.setBackground(new Color(255, 255, 140));
                    }
                }
            } else {
                component.setBackground(Color.LIGHT_GRAY);
            }
        }

        return component;
    }
}
