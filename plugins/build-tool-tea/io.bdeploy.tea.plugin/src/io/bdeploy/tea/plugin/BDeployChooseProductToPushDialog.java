/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tea.core.services.TaskingLog;

import io.bdeploy.api.product.v1.ProductManifestBuilder;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.tea.plugin.server.BDeployServerPanel;
import io.bdeploy.tea.plugin.server.BDeployTargetSpec;

public class BDeployChooseProductToPushDialog extends TitleAreaDialog {

    private Manifest.Key selected;
    private BDeployTargetSpec target;
    private final File hive;
    private Runnable buttonUpdate;
    private final TaskingLog log;

    public BDeployChooseProductToPushDialog(Shell parentShell, File hive, TaskingLog log) {
        super(parentShell);

        this.hive = hive;
        this.log = log;
    }

    public Manifest.Key getChosenProduct() {
        return selected;
    }

    public BDeployTargetSpec getChosenTarget() {
        return target;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("Select product to push");
        setMessage("Select destination BDeploy server and a previously built and cached product to push to it.",
                IMessageProvider.INFORMATION);

        Composite comp = new Composite(parent, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(comp);
        GridLayoutFactory.fillDefaults().margins(20, 20).applyTo(comp);

        BDeployServerPanel panel = new BDeployServerPanel(comp, log);
        GridDataFactory.fillDefaults().grab(true, true).hint(300, 150).applyTo(panel);

        TableViewer tv;
        tv = new TableViewer(comp, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        GridDataFactory.fillDefaults().grab(true, true).hint(300, 150).applyTo(tv.getControl());

        tv.setContentProvider(ArrayContentProvider.getInstance());
        tv.setComparator(new ViewerComparator() {

            @Override
            public int compare(Viewer viewer, Object e1, Object e2) {
                return ((Manifest) e2).getKey().getTag().compareTo(((Manifest) e1).getKey().getTag());
            }
        });
        tv.getTable().setHeaderVisible(true);

        TableViewerColumn keyCol = new TableViewerColumn(tv, SWT.NONE);
        keyCol.getColumn().setWidth(190);
        keyCol.getColumn().setText("ID");
        keyCol.setLabelProvider(new ColumnLabelProvider() {

            @Override
            public String getText(Object element) {
                return ((Manifest) element).getKey().getName();
            };
        });

        TableViewerColumn versionCol = new TableViewerColumn(tv, SWT.NONE);
        versionCol.getColumn().setWidth(180);
        versionCol.getColumn().setText("Version");
        versionCol.setLabelProvider(new ColumnLabelProvider() {

            @Override
            public String getText(Object element) {
                return ((Manifest) element).getKey().getTag();
            };
        });

        TableViewerColumn nameCol = new TableViewerColumn(tv, SWT.NONE);
        nameCol.getColumn().setWidth(140);
        nameCol.getColumn().setText("Name");
        nameCol.setLabelProvider(new ColumnLabelProvider() {

            @Override
            public String getText(Object element) {
                return ((Manifest) element).getLabels().get(ProductManifestBuilder.PRODUCT_LABEL);
            };
        });

        buttonUpdate = () -> {
            Button button = getButton(IDialogConstants.OK_ID);
            if (button != null) {
                button.setEnabled(selected != null && target != null);
            }
        };

        tv.addSelectionChangedListener((event) -> {
            IStructuredSelection sel = (IStructuredSelection) event.getSelection();
            if (sel == null || sel.isEmpty()) {
                selected = null;
            } else {
                selected = ((Manifest) sel.getFirstElement()).getKey();
            }
            buttonUpdate.run();
        });

        panel.addSelectionListener((server) -> {
            this.target = server;
            buttonUpdate.run();
        });

        ActivityReporter reporter = new ActivityReporter.Null();
        try (BHive bhive = new BHive(hive.toURI(), reporter)) {
            Set<Key> keys = bhive.execute(new ManifestListOperation());
            List<Manifest> pms = keys.stream().map(x -> bhive.execute(new ManifestLoadOperation().setManifest(x)))
                    .filter(x -> x.getLabels().containsKey(ProductManifestBuilder.PRODUCT_LABEL)).collect(Collectors.toList());
            tv.setInput(pms);
        }

        return comp;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);

        buttonUpdate.run();
    }

}
