/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin.server;

import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class BDeployLoginDialog extends TitleAreaDialog {

    private BDeployTargetSpec server;
    private final String serverName;
    private final String serverUrl;

    private String user;
    private String pass;
    private final boolean source;

    public BDeployLoginDialog(Shell parentShell, String serverName, String serverUrl, boolean source) {
        super(parentShell);
        this.serverName = serverName;
        this.serverUrl = serverUrl;
        this.source = source;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("Login to " + serverName);
        setMessage("Provide credentials for " + serverUrl, IMessageProvider.INFORMATION);

        Composite comp = new Composite(parent, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(comp);
        GridLayoutFactory.fillDefaults().numColumns(2).margins(20, 20).applyTo(comp);

        if (source) {
            Label lblSource = new Label(comp, SWT.NONE);
            lblSource.setText(
                    "Provide credentials for the software repository server to fetch missing required dependencies.\nThis is the server configured in the TEA BDeploy Preferences: "
                            + serverUrl);
            GridDataFactory.fillDefaults().grab(true, false).span(2, 1).applyTo(lblSource);
        }

        Label lblUser = new Label(comp, SWT.NONE);
        lblUser.setText("User");

        Text txtUser = new Text(comp, SWT.BORDER);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(txtUser);
        txtUser.addModifyListener((e) -> {
            user = txtUser.getText();
            updateState();
        });

        Label lblPass = new Label(comp, SWT.NONE);
        lblPass.setText("Password");

        Text txtPass = new Text(comp, SWT.BORDER | SWT.PASSWORD);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(txtPass);
        txtPass.addModifyListener((e) -> {
            pass = txtPass.getText();
            updateState();
        });

        return comp;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);

        updateState();
    }

    private void updateState() {
        getButton(OK).setEnabled(this.user != null && this.pass != null && !this.user.isEmpty() && !this.pass.isEmpty());
    }

    public BDeployTargetSpec getServer() {
        return server;
    }

    @Override
    protected void okPressed() {
        // perform login and create spec
        BDeployTargetSpec spec = new BDeployTargetSpec();
        spec.name = serverName;
        spec.uri = serverUrl;
        spec.token = login();

        if (spec.token == null) {
            return;
        }

        server = spec;

        // all is well, OK is allowed
        super.okPressed();
    }

    private String login() {
        ClientBuilder builder = ClientBuilder.newBuilder().hostnameVerifier((h, s) -> true).sslContext(createTrustAllContext());
        Response result = builder.build().target(serverUrl).path("/public/v1/login").queryParam("user", user)
                .queryParam("pass", pass).queryParam("full", "true").request().get();

        if (result.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
            setMessage("Error logging in to " + serverUrl + ": " + result.getStatusInfo().getReasonPhrase(),
                    IMessageProvider.ERROR);
            return null;
        } else {
            return result.readEntity(String.class);
        }
    }

    private SSLContext createTrustAllContext() {
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");

            sslcontext.init(null, new TrustManager[] { new X509TrustManager() {

                @Override
                public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            } }, new java.security.SecureRandom());
            return sslcontext;
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

}
