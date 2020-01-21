/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin.server;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import io.bdeploy.bhive.util.StorageHelper;

public class BDeployServerPanel extends Composite {

    private static final Image ADD_ICON = ImageDescriptor.createFromURL(ResourceHelper.locate("icons/add.png")).createImage();
    private static final Image EDIT_ICON = ImageDescriptor.createFromURL(ResourceHelper.locate("icons/edit.png")).createImage();
    private static final Image REMOVE_ICON = ImageDescriptor.createFromURL(ResourceHelper.locate("icons/remove.png"))
            .createImage();

    private final Preferences preferences;
    private BDeployTargetServers servers = new BDeployTargetServers();
    private TableViewer tv;
    private BDeployTargetSpec selected;
    private ToolBar tb;
    private ToolItem edit;
    private ToolItem remove;

    public BDeployServerPanel(Composite parent) {
        super(parent, SWT.NONE);

        preferences = InstanceScope.INSTANCE.getNode("io.bdeploy.tea.plugin.servers");

        load();
        createPanel();
    }

    private void load() {
        byte[] serverBytes = preferences.getByteArray("serverList", null);
        if (serverBytes == null) {
            return;
        }
        servers = StorageHelper.fromRawBytes(serverBytes, BDeployTargetServers.class);
    }

    private void save() {
        preferences.putByteArray("serverList", StorageHelper.toRawBytes(servers));
        try {
            preferences.flush();
        } catch (BackingStoreException e) {

        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        tv.getControl().setEnabled(enabled);
        tb.setEnabled(enabled);
    }

    private void createPanel() {
        GridLayoutFactory.swtDefaults().margins(0, 0).applyTo(this);

        tb = new ToolBar(this, SWT.NONE);
        GridDataFactory.swtDefaults().align(SWT.END, SWT.CENTER).applyTo(tb);

        ToolItem add = new ToolItem(tb, SWT.PUSH);
        add.setImage(ADD_ICON);
        add.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                add();
            }
        });

        edit = new ToolItem(tb, SWT.PUSH);
        edit.setImage(EDIT_ICON);
        edit.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                edit();
            }
        });

        remove = new ToolItem(tb, SWT.PUSH);
        remove.setImage(REMOVE_ICON);
        remove.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                remove();
            }
        });

        remove.setEnabled(false);
        edit.setEnabled(false);

        tv = new TableViewer(this, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(tv.getControl());

        tv.getTable().setHeaderVisible(true);
        tv.setComparator(new ViewerComparator() {

            @Override
            public int compare(Viewer viewer, Object e1, Object e2) {
                return ((BDeployTargetSpec) e1).name.compareTo(((BDeployTargetSpec) e2).name);
            }
        });

        TableViewerColumn nameCol = new TableViewerColumn(tv, SWT.NONE);
        nameCol.getColumn().setWidth(150);
        nameCol.getColumn().setText("Name");
        nameCol.setLabelProvider(new ColumnLabelProvider() {

            @Override
            public String getText(Object element) {
                return ((BDeployTargetSpec) element).name;
            };
        });

        TableViewerColumn uriCol = new TableViewerColumn(tv, SWT.NONE);
        uriCol.getColumn().setWidth(250);
        uriCol.getColumn().setText("URI");
        uriCol.setLabelProvider(new ColumnLabelProvider() {

            @Override
            public String getText(Object element) {
                return ((BDeployTargetSpec) element).uri;
            };
        });

        TableViewerColumn igCol = new TableViewerColumn(tv, SWT.NONE);
        igCol.getColumn().setWidth(150);
        igCol.getColumn().setText("Instance Group");
        igCol.setLabelProvider(new ColumnLabelProvider() {

            @Override
            public String getText(Object element) {
                return ((BDeployTargetSpec) element).instanceGroup;
            };
        });

        tv.setContentProvider(new ArrayContentProvider());
        tv.setInput(servers.servers);

        addSelectionListener(s -> {
            selected = s;
            remove.setEnabled(s != null);
            edit.setEnabled(s != null);
        });
    }

    public void addSelectionListener(BDeployServerSelectionListener listener) {
        this.tv.addSelectionChangedListener(new ISelectionChangedListener() {

            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                Object element = event.getStructuredSelection().getFirstElement();
                if (element instanceof BDeployTargetSpec) {
                    listener.selected((BDeployTargetSpec) element);
                }
            }
        });
    }

    private void add() {
        BDeployTargetSpec spec = new BDeployTargetSpec();
        if (new BDeployServerEditDialog(getShell(), spec).open() == Dialog.OK) {
            servers.servers.add(spec);
            save();
        }
        tv.setInput(servers.servers);
    }

    private void edit() {
        if (new BDeployServerEditDialog(getShell(), selected).open() == Dialog.OK) {
            save();
        } else {
            load();
        }
        tv.setInput(servers.servers);
    }

    private void remove() {
        if (MessageDialog.openConfirm(getShell(), "Delete", "This will permanently remove the server " + selected.name)) {
            servers.servers.remove(selected);
            save();
        }
        remove.setEnabled(false);
        edit.setEnabled(false);
        selected = null;
        tv.setInput(servers.servers);
    }

}
