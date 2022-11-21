/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.stream.Collectors;

import javax.net.ssl.SSLHandshakeException;

import org.eclipse.core.internal.variables.StringVariableManager;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.di.extensions.Service;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tea.core.BackgroundTask;
import org.eclipse.tea.core.TaskExecutionContext;
import org.eclipse.tea.core.annotations.TaskChainContextInit;
import org.eclipse.tea.core.annotations.TaskChainMenuEntry;
import org.eclipse.tea.core.services.TaskChain;
import org.eclipse.tea.core.services.TaskChain.TaskChainId;
import org.eclipse.tea.core.services.TaskingLog;
import org.eclipse.tea.core.ui.annotations.TaskChainUiInit;
import org.eclipse.tea.library.build.config.BuildDirectories;
import org.eclipse.tea.library.build.jar.ZipExecFactory;
import org.eclipse.tea.library.build.lcdsl.tasks.p2.DynamicProductBuildRegistry;
import org.eclipse.tea.library.build.menu.BuildLibraryMenu;
import org.eclipse.tea.library.build.services.TeaBuildVersionService;
import org.eclipse.tea.library.build.tasks.jar.TaskInitJarCache;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.prefs.BackingStoreException;

import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.remote.v1.PublicRootResource;
import io.bdeploy.api.remote.v1.dto.InstanceGroupConfigurationApi;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.tea.plugin.server.BDeployLoginDialog;
import io.bdeploy.tea.plugin.server.BDeployTargetSpec;
import io.bdeploy.tea.plugin.services.BDeployApplicationBuild;
import io.bdeploy.tea.plugin.services.BDeployApplicationDescriptor;
import io.bdeploy.tea.plugin.services.BDeployApplicationService;
import io.bdeploy.tea.plugin.services.BDeployApplicationService.CreateApplicationTasks;
import io.bdeploy.tea.plugin.services.BDeployProductBuild;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.UriBuilder;

@SuppressWarnings("restriction")
@Component
@TaskChainId(description = "Build BDeploy Product...", alias = "BDeployProduct")
@TaskChainMenuEntry(path = BuildLibraryMenu.MENU_BUILD, groupingId = "BDeploy", icon = "icons/bdeploy.png")
public class BDeployProductTaskChain implements TaskChain {

    private Path bdeployProductFile;
    private BDeployTargetSpec target;
    private BDeployTargetSpec source;
    private boolean cleanup;
    private boolean validate;

    @TaskChainUiInit
    public void uiInit(Shell parent, BDeployConfig cfg, TaskingLog log, BuildDirectories dirs, TeaBuildVersionService bvs)
            throws IOException, CoreException {
        // instance can be re-used - clear data.
        bdeployProductFile = null;
        target = null;

        if (cfg.bdeployProductListFile == null || cfg.bdeployProductListFile.isEmpty()) {
            throw new IllegalArgumentException("No BDeploy Product List File set in configuration");
        }

        String listFile = StringVariableManager.getDefault().performStringSubstitution(cfg.bdeployProductListFile);
        BDeployProductListDescriptor listDesc;
        Path listPath = Paths.get(listFile);
        try (InputStream is = Files.newInputStream(listPath)) {
            listDesc = StorageHelper.fromYamlStream(is, BDeployProductListDescriptor.class);
        }

        if (listDesc == null || listDesc.products.isEmpty()) {
            throw new IllegalStateException("Cannot find any product to build in the workspace");
        }

        Path rootPath = listPath.getParent();

        // Need to make sure that the source server is available for us, otherwise prompt login.
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode("io.bdeploy.tea.plugin.source");
        source = loadSourceServer(prefs);
        if (!checkServer(parent, source, cfg.bdeployServer, "Software Repository Server")) {
            source = null;
        }

        while (target == null) {
            BDeployChooseProductFileDialog dlg = new BDeployChooseProductFileDialog(parent, listDesc, log);
            dlg.setBlockOnOpen(true);
            if (dlg.open() != Dialog.OK) {
                throw new OperationCanceledException();
            }

            target = dlg.getChosenTarget();
            bdeployProductFile = rootPath.resolve(dlg.getChosenFile());
            cleanup = dlg.getCleanup();
            validate = dlg.getValidate();

            if (target == null) {
                // that's OK - package instead of push.
                break;
            }

            if (!checkServer(parent, target, null, "Target Server")) {
                target = null;
                bdeployProductFile = null;
            } else {
                // Target Server OK, lets see if the product version is OK to be built as configured.
                File hive = new File(dirs.getProductDirectory(), "bhive");
                if (!checkProductVersion(parent, target, cfg, log, bvs, hive)) {
                    target = null;
                    bdeployProductFile = null;
                }
            }
        }

        if (source == null) {
            BDeployLoginDialog srcDlg = new BDeployLoginDialog(parent, "BDeploy Software Repositories", cfg.bdeployServer, true,
                    log);
            srcDlg.setBlockOnOpen(true);
            if (srcDlg.open() != Dialog.OK) {
                throw new OperationCanceledException();
            }
            source = srcDlg.getServer();
            saveSourceServer(prefs, source);
        }
    }

    private boolean checkProductVersion(Shell parent, BDeployTargetSpec server, BDeployConfig cfg, TaskingLog log,
            TeaBuildVersionService bvs, File hive) throws IOException {
        ActivityReporter.Stream reporter = new ActivityReporter.Stream(log.info());
        try (BHive local = new BHive(hive.toURI(), null, reporter)) {
            BDeployProductDescriptor desc = readProductDescriptor();
            String fullVersion = BDeployBuildProductTask.calculateVersion(bvs, cfg, desc.productTag);

            Path pdi = bdeployProductFile.getParent().resolve(desc.productInfoYaml);
            String prodId;
            try (InputStream is = Files.newInputStream(pdi)) {
                prodId = StorageHelper.fromYamlStream(is, ProductDescriptor.class).product;
            }

            Manifest.Key prodKey = new Manifest.Key(prodId + "/product", fullVersion);

            if (!cfg.clearBHive && Boolean.TRUE.equals(local.execute(new ManifestExistsOperation().setManifest(prodKey)))) {
                // manifest already exists locally.
                MessageDialog.openWarning(parent, prodKey + " already exists locally", "A product version with the key " + prodKey
                        + " already exists in the local BHive (" + hive + ").\n\n"
                        + "You must remove the product version before building. It is not advisable to re-use product versions.");

                return false;
            }

            RemoteService svc = new RemoteService(UriBuilder.fromUri(server.uri).build(), server.token);
            try (RemoteBHive rbh = RemoteBHive.forService(svc, server.instanceGroup, reporter)) {
                SortedMap<Key, ObjectId> mi = rbh.getManifestInventory(prodKey.getName());
                if (mi.containsKey(prodKey)) {
                    MessageDialog.openWarning(parent, prodKey + " already exists remotely", "A product version with the key "
                            + prodKey + " already exists in the remote BHive (" + server.instanceGroup + " on " + server.uri
                            + ").\n\n"
                            + "This will result in a no-op when pushing the product, as it already exists remotely. You must remove the product version before building. It is not advisable to re-use product versions.");

                    return false;
                }
            }
        }
        return true;
    }

    private boolean checkServer(Shell parent, BDeployTargetSpec remote, String expectedServer, String serverName) {
        try {
            if (remote == null) {
                MessageDialog.openWarning(parent, serverName + " Configuration",
                        "No " + serverName + " authentication yet. You will need to re-login.");
                return false;
            }
            if (expectedServer != null && !remote.uri.equals(expectedServer)) {
                MessageDialog.openWarning(parent, remote.uri + " Configuration Changed",
                        "The configured " + serverName + " has changed. You will need to re-login.");
                return false;
            }
            RemoteService svc = new RemoteService(UriBuilder.fromUri(remote.uri).build(), remote.token);
            PublicRootResource master = JerseyClientFactory.get(svc).getProxyClient(PublicRootResource.class);

            // any actual remote call to verify the connection.
            List<InstanceGroupConfigurationApi> instanceGroups = master.getInstanceGroups();

            if (remote.instanceGroup != null && !remote.instanceGroup.isEmpty()) {
                Optional<InstanceGroupConfigurationApi> group = instanceGroups.stream()
                        .filter(ig -> ig.name.equals(remote.instanceGroup)).findAny();
                if (!group.isPresent()) {
                    MessageDialog.openWarning(parent, remote.uri + " Instance Group Missing", "The configured " + serverName
                            + " does not have the configured Instance Group " + remote.instanceGroup);
                    return false;
                }
            }
        } catch (ProcessingException pe) {
            if (pe.getCause() instanceof SSLHandshakeException) {
                MessageDialog.openWarning(parent, remote.uri + ": SSL Problem", "SSL Handshake failed. Likely, the configured "
                        + serverName + " received an updated certificate. You will need to re-login.");
            } else if (pe.getCause() instanceof ConnectException) {
                MessageDialog.openWarning(parent, remote.uri + ": Connection Problem", "Cannot connect to the configured "
                        + serverName + ". The connection was refused. Please make sure the server is running and reachable.");
            } else {
                MessageDialog.openWarning(parent, remote.uri + ": Unknown Error", "The connection to the configured " + serverName
                        + " failed for unknown reason. Please make sure it is configured correctly.\n\nThe exception was: "
                        + pe.toString());
            }
            return false;
        } catch (WebApplicationException we) {
            MessageDialog.openWarning(parent, remote.uri + ": Error", "The " + serverName
                    + " responded, but refused the connection. Make sure the configured user is authorized. You will need to re-login.\n\nThe exception was: "
                    + we.toString());
            return false;
        } catch (Exception e) {
            MessageDialog.openWarning(parent, remote.uri + ": Unavailable", "The configured " + serverName
                    + " cannot be contacted for unknown reason. Please make sure it is available before building a product.\n\nThe exception was: "
                    + e.toString());
            return false;
        }
        return true;
    }

    private BDeployTargetSpec loadSourceServer(IEclipsePreferences prefs) {
        byte[] serverBytes = prefs.getByteArray("sourceServer", null);
        if (serverBytes == null) {
            return null;
        }
        return StorageHelper.fromRawBytes(serverBytes, BDeployTargetSpec.class);
    }

    private void saveSourceServer(IEclipsePreferences preferences, BDeployTargetSpec source) {
        preferences.putByteArray("sourceServer", StorageHelper.toRawBytes(source));
        try {
            preferences.flush();
        } catch (BackingStoreException e) {
        }
    }

    @SuppressWarnings("unchecked")
    @TaskChainContextInit
    public void init(TaskExecutionContext c, TaskingLog log, BDeployConfig cfg, DynamicProductBuildRegistry registry,
            BuildDirectories dirs, @Service List<BDeployApplicationService> appServices, IEclipseContext ctx)
            throws CoreException {

        File hive = new File(dirs.getProductDirectory(), "bhive");

        // to produce reproducible JAR files (timestamps).
        ZipExecFactory.setIgnoreExternalZipExe(true);

        c.addTask(new BDeployCheckServerOnlineTask(target, source));

        if (bdeployProductFile == null && cfg.bdeployProductFile != null) {
            bdeployProductFile = Paths.get(cfg.bdeployProductFile);
        }

        if (bdeployProductFile == null) {
            throw new IllegalStateException("BDeploy is not configured, set configuration");
        }

        if (!Files.exists(bdeployProductFile)) {
            throw new IllegalStateException("Configuration file does not exist: " + bdeployProductFile);
        }

        BDeployProductDescriptor desc = readProductDescriptor();

        BDeployProductBuild pd = new BDeployProductBuild();
        pd.productInfo = bdeployProductFile.getParent().resolve(desc.productInfoYaml);
        pd.productTag = desc.productTag;

        if (validate && desc.validationYaml != null) {
            Path validationYaml = bdeployProductFile.getParent().resolve(desc.validationYaml);

            if (!Files.exists(validationYaml)) {
                throw new IllegalStateException("Validation YAML does not exist: " + validationYaml);
            }

            c.addTask(new BDeployValidateProductTask(target == null ? source : target, validationYaml));
        }

        TaskInitJarCache cache = new TaskInitJarCache(dirs.getNewCacheDirectory("jar"));
        c.addTask(cache);

        for (BDeployApplicationDescriptor app : desc.applications) {
            Optional<BDeployApplicationService> handler = appServices.stream().filter(s -> s.canHandle(app.type)).findFirst();
            if (handler.isPresent()) {
                IEclipseContext child = ctx.createChild("Create BDeploy Application " + app.name);
                child.set(BDeployApplicationDescriptor.class, app);

                Object o = ContextInjectionFactory.invoke(handler.get(), CreateApplicationTasks.class, child);
                if (!(o instanceof List)) {
                    throw new IllegalStateException("Service " + handler.get() + " did not return a list of "
                            + BDeployApplicationBuild.class.getSimpleName());
                }

                pd.apps.addAll((List<BDeployApplicationBuild>) o);

                child.dispose();
            } else {
                throw new IllegalArgumentException("Unknown application type: " + app.type);
            }
        }

        c.addTask(BackgroundTask
                .allBarrier(pd.apps.stream().map(a -> a.task).filter(Objects::nonNull).collect(Collectors.toList())));

        BDeployBuildProductTask build = new BDeployBuildProductTask(pd, hive, target, source, cleanup);
        c.addTask(build);

        if (target == null && cfg.bdeployProductPushServer != null) {
            target = new BDeployTargetSpec();
            target.name = "Configured by headless build";
            target.uri = cfg.bdeployProductPushServer;
            target.token = cfg.bdeployProductPushToken;
            target.instanceGroup = cfg.bdeployProductPushGroup;
        }

        if (target != null) {
            c.addTask(new BDeployProductPushTask(hive, () -> build.getKey(), target));
        } else {
            c.addTask(new BDeployPackageProductTask(build));
        }
        c.addTask(cache.getCleanup());
    }

    private BDeployProductDescriptor readProductDescriptor() {
        try (InputStream is = Files.newInputStream(bdeployProductFile)) {
            return StorageHelper.fromYamlStream(is, BDeployProductDescriptor.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read configuration in " + bdeployProductFile, e);
        }
    }

}
