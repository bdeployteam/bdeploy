package io.bdeploy.minion.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.function.Function;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.remote.jersey.BHiveJacksonModule;
import io.bdeploy.bhive.remote.jersey.BHiveLocatorImpl;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TaskSynchronizer;
import io.bdeploy.common.audit.AuditRecord;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.MinionRootValidator;
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.plugin.PluginManager;
import io.bdeploy.interfaces.plugin.VersionSorterService;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.jersey.RegistrationTarget;
import io.bdeploy.jersey.actions.ActionBridge;
import io.bdeploy.jersey.actions.ActionService;
import io.bdeploy.jersey.ws.change.ObjectChangeBroadcaster;
import io.bdeploy.jersey.ws.change.ObjectChangeWebSocket;
import io.bdeploy.minion.ConnectivityChecker;
import io.bdeploy.minion.ControllingMasterProvider;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.MinionState;
import io.bdeploy.minion.api.v1.PublicProductValidationResourceImpl;
import io.bdeploy.minion.api.v1.PublicRootResourceImpl;
import io.bdeploy.minion.cli.StartTool.MasterConfig;
import io.bdeploy.minion.cli.shutdown.RemoteShutdownImpl;
import io.bdeploy.minion.plugin.VersionSorterServiceImpl;
import io.bdeploy.minion.remote.jersey.CentralUpdateResourceImpl;
import io.bdeploy.minion.remote.jersey.CommonDirectoryEntryResourceImpl;
import io.bdeploy.minion.remote.jersey.CommonRootResourceImpl;
import io.bdeploy.minion.remote.jersey.JerseyAwareMinionRestartManager;
import io.bdeploy.minion.remote.jersey.MasterRootResourceImpl;
import io.bdeploy.minion.remote.jersey.MasterSettingsResourceImpl;
import io.bdeploy.minion.remote.jersey.MinionStatusResourceImpl;
import io.bdeploy.minion.remote.jersey.MinionUpdateResourceImpl;
import io.bdeploy.minion.remote.jersey.NodeCleanupResourceImpl;
import io.bdeploy.minion.remote.jersey.NodeDeploymentResourceImpl;
import io.bdeploy.minion.remote.jersey.NodeProcessResourceImpl;
import io.bdeploy.minion.remote.jersey.NodeProxyResourceImpl;
import io.bdeploy.schema.SchemaResources;
import io.bdeploy.ui.api.AuthGroupService;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManager;
import io.bdeploy.ui.api.impl.ChangeEventManagerToNodeManagerInitializer;
import io.bdeploy.ui.api.impl.UiResources;
import jakarta.inject.Singleton;

/**
 * Starts a HTTPS server which accepts API calls depending on the mode of the given root directory.
 */
@Help("Start a node or master node.")
@ToolCategory(MinionServerCli.SERVER_TOOLS)
@CliName(value = "start", alias = { "master", "slave" })
public class StartTool extends ConfiguredCliTool<MasterConfig> {

    private static final Logger log = LoggerFactory.getLogger(StartTool.class);

    public @interface MasterConfig {

        @Help("Root directory for the master minion. Must be initialized using the init command.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator({ MinionRootValidator.class, PathOwnershipValidator.class })
        String root();

        @Help("Specify the directory where any incoming updates should be placed in.")
        String updateDir();

        @Help(value = "Publish the web application, defaults to true.", arg = false)
        boolean publishWebapp() default true;

        @Help("A token which can be used to remotely shutdown the server on /shutdown")
        String shutdownToken();

        @Help("Disable logging to file, instead log to console.")
        boolean consoleLog() default false;

        @Help(value = "Skip the check for a valid host/port configuration", arg = false)
        boolean skipConnectionCheck() default false;
    }

    public StartTool() {
        super(MasterConfig.class);
    }

    @Override
    protected RenderableResult run(MasterConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");

        try (MinionRoot r = new MinionRoot(Paths.get(config.root()), new ActivityReporter.Null())) {
            r.getAuditor().audit(AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("start").build());

            out().println("Starting " + r.getMode() + "...");
            log.info("Starting {} / {} ...", VersionHelper.getVersionAsString(), r.getMode());

            if (!config.skipConnectionCheck()) {
                conCheck(r);
            }

            if (config.updateDir() != null) {
                Path upd = Paths.get(config.updateDir());
                r.setUpdateDir(upd);
            }

            MinionState state = r.getState();
            SecurityHelper sh = SecurityHelper.getInstance();
            KeyStore ks = sh.loadPrivateKeyStore(state.keystorePath, state.keystorePass);
            KeyStore https = null;

            if (state.keystoreHttpsPath != null && PathHelper.exists(state.keystoreHttpsPath)) {
                https = sh.loadPrivateKeyStore(state.keystoreHttpsPath, state.keystorePass);
            }

            try (JerseyServer srv = new JerseyServer(state.port, ks, https, state.keystorePass, r.getSessionConfiguration())) {
                BHiveRegistry reg = setupServerCommon(new ActivityReporter.Null(), r, srv, config);

                if (r.getMode() != MinionMode.NODE) {
                    // MASTER (standalone, managed, central)
                    setupServerMaster(config, r, srv, reg);
                } else {
                    setupServerNode(config, srv);
                }

                if (config.shutdownToken() != null) {
                    srv.register(new RemoteShutdownImpl(srv, config.shutdownToken()));
                }

                srv.start();
                srv.join();

                return createSuccess();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot start server", e);
        }
    }

    private void conCheck(MinionRoot r) {
        try {
            // we're way earlier than any node manager, so we need to read information ourselves.
            MinionManifest mm = new MinionManifest(r.getHive());
            MinionConfiguration cfg = mm.read();

            MinionDto minion = cfg.getMinion(r.getState().self);
            if (minion == null) {
                throw new IllegalStateException("Cannot find my own remote configuration in minion manifest");
            }
            ConnectivityChecker.checkOrThrow(minion.remote);
        } catch (Exception e) {
            log.error("ATTENTION: CANNOT CONNECT TO SELF", e);

            // we anyhow continue just in case to not doom existing installations after update.
            // the web UI will show a message to the user in case this happens.
            r.markConnectionCheckFailed();
        }
    }

    private void setupServerNode(MasterConfig config, JerseyServer srv) {
        if (config.publishWebapp()) {
            UiResources.registerNode(srv);
        }
    }

    private void setupServerMaster(MasterConfig config, MinionRoot r, JerseyServer srv, BHiveRegistry reg) {
        srv.setUserValidator(user -> {
            UserInfo info = r.getUsers().getUser(user);
            if (info == null) {
                if (user.startsWith("[") && user.endsWith("]")) {
                    // on behalf of remote user (e.g. from central).
                    return true;
                }
                log.error("User not available: {}. Allowing to support legacy tokens.", user);
                return true;
            }
            return r.getUsers().isAuthenticationValid(info);
        });
        registerMasterResources(srv, reg, config.publishWebapp(), r, r.createPluginManager(srv), getAuditorFactory(),
                r.isInitialConnectionCheckFailed());
    }

    private BHiveRegistry setupServerCommon(ActivityReporter repo, MinionRoot r, JerseyServer srv, MasterConfig config) {
        r.onStartup(config.consoleLog());
        srv.afterStartup().thenRun(() -> r.afterStartup(false));

        srv.setAuditor(r.getAuditor());
        r.setRestartManager(new JerseyAwareMinionRestartManager(srv));
        r.setupServerTasks(r.getMode());

        return registerCommonResources(srv, r, repo);
    }

    public static void registerMasterResources(RegistrationTarget srv, BHiveRegistry reg, boolean webapp, MinionRoot minionRoot,
            PluginManager pluginManager, Function<Path, Auditor> auditorFactory, boolean conCheckFailed) {

        if (minionRoot.getMode() == MinionMode.CENTRAL) {
            srv.register(CentralUpdateResourceImpl.class);
        } else {
            srv.register(MasterRootResourceImpl.class);
        }

        srv.register(CommonRootResourceImpl.class);
        srv.register(PublicRootResourceImpl.class);
        srv.register(PublicProductValidationResourceImpl.class);
        srv.register(MasterSettingsResourceImpl.class);
        srv.register(new ChangeEventManagerToNodeManagerInitializer(minionRoot.getNodeManager()));

        srv.register(new AbstractBinder() {

            @Override
            protected void configure() {
                // required for SoftwareUpdateResourceImpl.
                bind(MasterRootResourceImpl.class).to(MasterRootResource.class);
                bind(minionRoot.getNodeManager()).to(NodeManager.class);
                bind(ControllingMasterProvider.class).in(Singleton.class).to(MasterProvider.class);
                if (pluginManager != null) {
                    bind(pluginManager).to(PluginManager.class);
                }
                bind(new VersionSorterServiceImpl(pluginManager, reg)).to(VersionSorterService.class);

                if (minionRoot.getMode() == MinionMode.CENTRAL) {
                    bind(new ActionBridge(minionRoot.getActions())).to(ActionBridge.class);
                }
            }
        });

        if (webapp) {
            UiResources.register(srv, conCheckFailed);
        }

        // scan storage locations, register hives
        minionRoot.getStorageLocations().forEach(s -> reg.scanLocation(s, auditorFactory));
    }

    public static BHiveRegistry registerCommonResources(RegistrationTarget srv, MinionRoot root, ActivityReporter reporter) {
        Function<BHive, Permission> hivePermissionClassifier = h -> {
            if (new SoftwareRepositoryManifest(h).read() != null) {
                // in case of software repos, we want to grant read access to ANYBODY.
                return null;
            }
            return Permission.READ;
        };

        BHiveRegistry r = new BHiveRegistry(reporter, hivePermissionClassifier);

        // register the root hive as default.
        r.register(JerseyRemoteBHive.DEFAULT_NAME, root.getHive());

        srv.register(BHiveLocatorImpl.class);
        srv.register(r.binder());
        srv.register(new BHiveJacksonModule().binder());
        srv.register(MinionStatusResourceImpl.class);
        srv.register(MinionUpdateResourceImpl.class);
        srv.register(CommonDirectoryEntryResourceImpl.class);

        if (root.getMode() != MinionMode.CENTRAL) {
            srv.register(NodeCleanupResourceImpl.class);
            srv.register(NodeProcessResourceImpl.class);
            srv.register(NodeDeploymentResourceImpl.class);
            srv.register(NodeProxyResourceImpl.class);
        }

        ObjectChangeWebSocket ocws = new ObjectChangeWebSocket(srv.getKeyStore());
        srv.registerWebsocketApplication(ObjectChangeWebSocket.OCWS_PATH, ocws);

        srv.register(new MinionCommonBinder(root, ocws));
        srv.registerResource(r);

        SchemaResources.register(srv);

        return r;
    }

    private static class MinionCommonBinder extends AbstractBinder {

        private final MinionRoot root;
        private final ObjectChangeWebSocket ocws;

        public MinionCommonBinder(MinionRoot root, ObjectChangeWebSocket ocws) {
            this.root = root;
            this.ocws = ocws;
        }

        @Override
        protected void configure() {
            bind(root).to(MinionRoot.class);
            bind(root).to(Minion.class);
            bind(root.createActionService(ocws)).to(ActionService.class);
            bind(root.getUsers()).to(AuthService.class);
            bind(root.getUserGroups()).to(AuthGroupService.class);
            bind(root.getState().storageMinFree).named(JerseyServer.FILE_SYSTEM_MIN_SPACE).to(Long.class);
            bind(ocws).to(ObjectChangeBroadcaster.class);
            bind(new TaskSynchronizer()).to(TaskSynchronizer.class);
        }
    }

}
