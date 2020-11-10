package io.bdeploy.launcher.cli.ui.browser;

import javax.swing.RowFilter;

import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.launcher.cli.ClientApplicationDto;
import io.bdeploy.launcher.cli.ClientSoftwareConfiguration;

public class BrowserDialogTableFilter extends RowFilter<BrowserDialogTableModel, Integer> {

    private final String filterText;

    public BrowserDialogTableFilter(String text) {
        this.filterText = text;
    }

    @Override
    public boolean include(Entry<? extends BrowserDialogTableModel, ? extends Integer> entry) {
        if (filterText.isEmpty()) {
            return true;
        }
        BrowserDialogTableModel model = entry.getModel();
        ClientSoftwareConfiguration app = model.get(entry.getIdentifier());

        // Check metadata
        ClientApplicationDto metadata = app.metadata;
        if (metadata != null) {
            if (contains(metadata.appName)) {
                return true;
            }
            if (contains(metadata.instanceGroupTitle)) {
                return true;
            }
            if (contains(metadata.instanceName)) {
                return true;
            }
            if (contains(metadata.purpose.toString())) {
                return true;
            }
            if (contains(metadata.product.getTag())) {
                return true;
            }
        }

        // Check ClickAndStartDescriptor
        ClickAndStartDescriptor clickAndStart = app.clickAndStart;
        if (contains(clickAndStart.groupId)) {
            return true;
        }
        if (contains(clickAndStart.instanceId)) {
            return true;
        }
        if (contains(clickAndStart.applicationId)) {
            return true;
        }
        if (contains(clickAndStart.host.getUri().toString())) {
            return true;
        }
        return false;
    }

    private boolean contains(String value) {
        if (value == null) {
            return false;
        }
        return value.toLowerCase().contains(filterText);
    }

}
