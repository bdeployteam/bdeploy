/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import java.io.File;
import java.util.SortedSet;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.interfaces.manifest.ProductManifest;

public class BDeployChooseProductToPushDialog extends TitleAreaDialog {

    private Manifest.Key selected;
    private final File hive;

    public BDeployChooseProductToPushDialog(Shell parentShell, File hive) {
        super(parentShell);

        this.hive = hive;
    }

    public Manifest.Key getChosenProduct() {
        return selected;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("Select product to push");
        setMessage("Select a previously built and cached product to push to the configured BDeploy server.",
                IMessageProvider.INFORMATION);

        Composite comp = new Composite(parent, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(comp);
        GridLayoutFactory.fillDefaults().margins(20, 20).applyTo(comp);

        TableViewer tv;
        tv = new TableViewer(comp, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        GridDataFactory.fillDefaults().grab(true, true).hint(300, 300).applyTo(tv.getControl());

        tv.setContentProvider(ArrayContentProvider.getInstance());
        tv.setLabelProvider(new LabelProvider());
        tv.addSelectionChangedListener(new ISelectionChangedListener() {

            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                IStructuredSelection sel = (IStructuredSelection) event.getSelection();
                if (sel == null || sel.isEmpty()) {
                    selected = null;
                } else {
                    selected = (Manifest.Key) sel.getFirstElement();
                }
                Button button = getButton(IDialogConstants.OK_ID);
                if (button != null) {
                    button.setEnabled(selected != null);
                }
            }
        });

        ActivityReporter reporter = new ActivityReporter.Null();
        try (BHive bhive = new BHive(hive.toURI(), reporter)) {
            SortedSet<Key> keys = ProductManifest.scan(bhive);
            tv.setInput(keys);
        }

        return comp;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);

        getButton(IDialogConstants.OK_ID).setEnabled(selected != null);
    }

}
