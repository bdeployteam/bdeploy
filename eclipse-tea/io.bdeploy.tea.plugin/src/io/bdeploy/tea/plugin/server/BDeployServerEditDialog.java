/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin.server;

import java.util.List;
import java.util.Optional;

import javax.ws.rs.core.UriBuilder;

import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import io.bdeploy.api.remote.v1.PublicRootResource;
import io.bdeploy.api.remote.v1.dto.InstanceGroupConfigurationApi;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.JerseyClientFactory;

public class BDeployServerEditDialog extends TitleAreaDialog {

    private final BDeployTargetSpec template;
    private ComboViewer comboIg;
    private Button btnLoad;
    private Button btnLogin;

    public BDeployServerEditDialog(Shell parentShell, BDeployTargetSpec template) {
        super(parentShell);
        this.template = template;

        setBlockOnOpen(true);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("Configure BDeploy Server");
        setMessage("Provide connection details for the server.", IMessageProvider.INFORMATION);

        Composite comp = new Composite(parent, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(comp);
        GridLayoutFactory.fillDefaults().numColumns(3).margins(20, 20).applyTo(comp);

        Label lblName = new Label(comp, SWT.NONE);
        lblName.setText("Display Name");

        Text txtName = new Text(comp, SWT.BORDER);
        txtName.setText(template.name);
        GridDataFactory.fillDefaults().grab(true, false).span(2, 1).applyTo(txtName);
        txtName.addModifyListener((e) -> {
            template.name = txtName.getText();
            updateState();
        });

        Label lblUri = new Label(comp, SWT.NONE);
        lblUri.setText("BDeploy Server URI");

        Text txtUri = new Text(comp, SWT.BORDER);
        txtUri.setText(template.uri);
        GridDataFactory.fillDefaults().grab(true, false).span(2, 1).applyTo(txtUri);
        txtUri.addModifyListener((e) -> {
            template.uri = txtUri.getText();
            updateState();
        });

        Label lblToken = new Label(comp, SWT.NONE);
        lblToken.setText("BDeploy Server Token");

        Text txtToken = new Text(comp, SWT.BORDER);
        txtToken.setEnabled(false);
        txtToken.setText(template.token);
        GridDataFactory.fillDefaults().grab(true, false).hint(150, -1).applyTo(txtToken);
        txtToken.addModifyListener((e) -> {
            template.token = txtToken.getText();
            updateState();
        });

        btnLogin = new Button(comp, SWT.PUSH);
        btnLogin.setText("Login");
        btnLogin.setEnabled(false);
        btnLogin.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                BDeployLoginDialog dlg = new BDeployLoginDialog(getShell(), txtName.getText(), txtUri.getText(), false);
                dlg.setBlockOnOpen(true);
                if (dlg.open() == OK) {
                    txtToken.setText(dlg.getServer().token);
                }
            }
        });

        Label lblIg = new Label(comp, SWT.NONE);
        lblIg.setText("Target Instance Group");

        comboIg = new ComboViewer(comp, SWT.BORDER);
        comboIg.getCombo().setText(template.instanceGroup);
        comboIg.setContentProvider(ArrayContentProvider.getInstance());
        GridDataFactory.fillDefaults().grab(true, false).applyTo(comboIg.getControl());
        comboIg.getCombo().addModifyListener((e) -> {
            template.instanceGroup = comboIg.getCombo().getText();
            updateState();
        });

        btnLoad = new Button(comp, SWT.PUSH);
        btnLoad.setText("Load Groups");
        btnLoad.setEnabled(false);
        btnLoad.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                RemoteService svc = new RemoteService(UriBuilder.fromUri(template.uri).build(), template.token);
                try {
                    PublicRootResource root = JerseyClientFactory.get(svc).getProxyClient(PublicRootResource.class);
                    List<InstanceGroupConfigurationApi> igs = root.getInstanceGroups();

                    String sel = comboIg.getCombo().getText();
                    comboIg.setInput(igs.stream().map(i -> i.name).toArray());
                    comboIg.setSelection(new StructuredSelection(sel));
                } catch (Exception ex) {
                    MessageDialog.openError(getShell(), "Error", "Cannot load instance groups: " + ex.getMessage());
                }
            };
        });

        return comp;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);

        createButton(parent, 100, "Verify", false);
        getButton(100).addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                RemoteService svc = new RemoteService(UriBuilder.fromUri(template.uri).build(), template.token);
                try {
                    PublicRootResource root = JerseyClientFactory.get(svc).getProxyClient(PublicRootResource.class);
                    List<InstanceGroupConfigurationApi> igs = root.getInstanceGroups();
                    Optional<InstanceGroupConfigurationApi> ig = igs.stream().filter(i -> i.name.equals(template.instanceGroup))
                            .findAny();
                    if (!ig.isPresent()) {
                        throw new IllegalArgumentException(
                                "Instance Group " + template.instanceGroup + " does not exist on the Server.");
                    }
                    MessageDialog.openInformation(getShell(), "OK", "Server information verified successfully");
                } catch (Exception ex) {
                    MessageDialog.openError(getShell(), "Error", "Cannot verify server: " + ex.getMessage());
                }
            }
        });

        updateState();
    }

    private void updateState() {
        boolean enable = false;
        boolean canVerify = true;
        boolean canLoad = true;
        boolean canLogin = true;

        if (template.name.isEmpty()) {
            setMessage("Provide a display name", IMessageProvider.ERROR);
        }
        if (template.instanceGroup.isEmpty()) {
            canVerify = false;
            setMessage("Provide an instance group name", IMessageProvider.ERROR);
        }
        if (template.token.isEmpty()) {
            canVerify = false;
            canLoad = false;
            setMessage("Perform a Login", IMessageProvider.ERROR);
        }
        if (template.uri.isEmpty() || !template.uri.startsWith("https") || !template.uri.endsWith("/api")) {
            canVerify = false;
            canLoad = false;
            canLogin = false;
            setMessage("Provide an URI with the format 'https://host:port/api'", IMessageProvider.ERROR);
        }

        if (canVerify && !template.name.isEmpty()) {
            setMessage("Provide connection details for the server.", IMessageProvider.INFORMATION);
            enable = true;
        }

        getButton(100).setEnabled(canVerify);
        btnLogin.setEnabled(canLogin);
        btnLoad.setEnabled(canLoad);
        getButton(OK).setEnabled(enable);
    }

}
