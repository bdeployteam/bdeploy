/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import java.util.Map;

import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tea.core.services.TaskingLog;

import io.bdeploy.tea.plugin.server.BDeployServerPanel;
import io.bdeploy.tea.plugin.server.BDeployTargetSpec;

public class BDeployChooseProductFileDialog extends TitleAreaDialog {

    private String selected;
    private BDeployTargetSpec target;
    private boolean cleanup = true;
    private final BDeployProductListDescriptor products;
    private Runnable buttonUpdate;
    private final TaskingLog log;

    public BDeployChooseProductFileDialog(Shell parentShell, BDeployProductListDescriptor products, TaskingLog log) {
        super(parentShell);

        this.products = products;
        this.log = log;
    }

    public String getChosenFile() {
        return selected;
    }

    public BDeployTargetSpec getChosenTarget() {
        return target;
    }

    public boolean getCleanup() {
        return cleanup;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("Select product to build");
        setMessage("Select the product to build/push to BDeploy", IMessageProvider.INFORMATION);

        Composite comp = new Composite(parent, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(comp);
        GridLayoutFactory.fillDefaults().margins(20, 20).applyTo(comp);

        TableViewer tv;
        tv = new TableViewer(comp, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION);
        GridDataFactory.fillDefaults().grab(true, true).hint(300, 150).applyTo(tv.getControl());
        tv.setContentProvider(ArrayContentProvider.getInstance());
        tv.getTable().setHeaderVisible(true);

        TableViewerColumn nameCol = new TableViewerColumn(tv, SWT.NONE);
        nameCol.getColumn().setWidth(200);
        nameCol.getColumn().setText("Name");
        nameCol.setLabelProvider(new ColumnLabelProvider() {

            @Override
            public String getText(Object element) {
                return ((Map.Entry<String, String>) element).getKey();
            };
        });

        TableViewerColumn fileCol = new TableViewerColumn(tv, SWT.NONE);
        fileCol.getColumn().setWidth(200);
        fileCol.getColumn().setText("File");
        fileCol.setLabelProvider(new ColumnLabelProvider() {

            @Override
            public String getText(Object element) {
                return ((Map.Entry<String, String>) element).getValue();
            };
        });

        tv.setInput(products.products.entrySet());

        Button radioZip = new Button(comp, SWT.RADIO);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(radioZip);
        radioZip.setText("Compress result and archive in the Workspace");

        Button radioPush = new Button(comp, SWT.RADIO);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(radioPush);
        radioPush.setText("Push result to selected target:");

        BDeployServerPanel panel = new BDeployServerPanel(comp, log);
        GridDataFactory.fillDefaults().hint(300, 150).applyTo(panel);

        Button checkCleanup = new Button(comp, SWT.CHECK);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(checkCleanup);
        checkCleanup.setText("Cleanup Temporary Data (Applications, Product Files) after build.");
        checkCleanup.setSelection(cleanup);

        buttonUpdate = () -> {
            Button button = getButton(OK);
            if (button != null) {
                button.setEnabled(selected != null && (target != null || radioZip.getSelection()));
            }
        };

        tv.addSelectionChangedListener(event -> {
            IStructuredSelection sel = (IStructuredSelection) event.getSelection();
            if (sel == null || sel.isEmpty()) {
                selected = null;
            } else {
                selected = ((Map.Entry<String, String>) sel.getFirstElement()).getValue();
            }
            buttonUpdate.run();

        });

        panel.addSelectionListener(s -> {
            target = s;
            buttonUpdate.run();
        });

        radioPush.setSelection(true);

        radioZip.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                panel.setEnabled(!radioZip.getSelection());
                buttonUpdate.run();
            }
        });

        checkCleanup.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                cleanup = checkCleanup.getSelection();
            }
        });

        return comp;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);

        buttonUpdate.run();
    }

}
