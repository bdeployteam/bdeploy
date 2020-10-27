package io.bdeploy.minion.cli;

import static io.bdeploy.common.util.RuntimeAssert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Map;
import java.util.function.Function;

import org.glassfish.hk2.utilities.binding.AbstractBinder;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.remote.jersey.BHiveJacksonModule;
import io.bdeploy.bhive.remote.jersey.BHiveLocatorImpl;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.MinionRootValidator;
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.jersey.RegistrationTarget;
import io.bdeploy.jersey.activity.JerseyBroadcastingActivityReporter;
import io.bdeploy.jersey.audit.AuditRecord;
import io.bdeploy.jersey.audit.RollingFileAuditor;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.jersey.ws.BroadcastingAuthenticatedWebSocket;
import io.bdeploy.jersey.ws.JerseyEventBroadcaster;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.MinionState;
import io.bdeploy.minion.cli.SlaveTool.SlaveConfig;
import io.bdeploy.minion.remote.jersey.JerseyAwareMinionUpdateManager;
import io.bdeploy.minion.remote.jersey.MinionStatusResourceImpl;
import io.bdeploy.minion.remote.jersey.MinionUpdateResourceImpl;
import io.bdeploy.minion.remote.jersey.SlaveCleanupResourceImpl;
import io.bdeploy.minion.remote.jersey.SlaveDeploymentResourceImpl;
import io.bdeploy.minion.remote.jersey.SlaveProcessResourceImpl;
import io.bdeploy.minion.remote.jersey.SlaveProxyResourceImpl;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;

/**
 * Manages slaves.
 */
@Help("Manage minions on a master, or start a non-master minion.")
@ToolCategory(MinionServerCli.SERVER_TOOLS)
@CliName("slave")
public class SlaveTool extends RemoteServiceTool<SlaveConfig> {

    public @interface SlaveConfig {

        @Help("Root directory for the master minion. The minion will put all required things here.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator({ MinionRootValidator.class, PathOwnershipValidator.class })
        String root();

        @Help("Specify the directory where any incoming updates should be placed in.")
        String updateDir();

        @Help("Adds a minions with the given name.")
        String add();

        @Help("The name of the minions to remove.")
        String remove();

        @Help(value = "When given, list all known minions.", arg = false)
        boolean list() default false;
    }

    public SlaveTool() {
        super(SlaveConfig.class);
    }

    @Override
    protected RenderableResult run(SlaveConfig config, @RemoteOptional RemoteService svc) {
        helpAndFailIfMissing(config.root(), "Missing --root");
        ActivityReporter.Delegating delegate = new ActivityReporter.Delegating();
        delegate.setDelegate(getActivityReporter());
        try (MinionRoot r = new MinionRoot(Paths.get(config.root()), delegate)) {
            r.getAuditor().audit(AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("slave").build());
            if (config.updateDir() != null) {
                Path upd = Paths.get(config.updateDir());
                r.setUpdateDir(upd);
            }
            if (config.list()) {
                return doListMinions(r);
            } else if (config.add() != null) {
                return doAddMinion(r, config.add(), svc);
            } else if (config.remove() != null) {
                return doRemoveMinion(r, config.remove());
            } else {
                if (r.getMode() != MinionMode.SLAVE) {
                    throw new IllegalStateException("Not a SLAVE root: " + config.root());
                }
                doRunMinion(r, delegate);
                return null; // usually not reached.
            }
        }
    }

    private void doRunMinion(MinionRoot root, ActivityReporter.Delegating delegate) {
        MinionState state = root.getState();
        int port = state.port;
        try {
            out().println("Starting slave...");

            SecurityHelper sh = SecurityHelper.getInstance();
            KeyStore ks = sh.loadPrivateKeyStore(state.keystorePath, state.keystorePass);
            try (JerseyServer srv = new JerseyServer(port, ks, state.keystorePass)) {
                srv.setAuditor(new RollingFileAuditor(root.getAuditLogDir()));
                root.setUpdateManager(new JerseyAwareMinionUpdateManager(srv));
                root.onStartup();

                delegate.setDelegate(srv.getRemoteActivityReporter());
                registerCommonResources(srv, root, srv.getRemoteActivityReporter());
                root.setupServerTasks(false, null);

                srv.start();
                srv.join();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot run server on " + port, e);
        }
    }

    private DataResult doAddMinion(MinionRoot root, String minionName, RemoteService slaveRemote) {
        helpAndFailIfMissing(root, "Missing --remote");
        assertTrue(slaveRemote.getUri().getScheme().equalsIgnoreCase("https"), "Only HTTPS slaves supported");
        try {
            // Try to contact the slave to get some information
            MinionStatusResource statusResource = ResourceProvider.getResource(slaveRemote, MinionStatusResource.class,
                    getLocalContext());
            MinionStatusDto status = statusResource.getStatus();

            // Store information in our hive
            MinionManifest mf = new MinionManifest(root.getHive());
            MinionConfiguration minionConfig = mf.read();
            minionConfig.addMinion(minionName, status.config);
            mf.update(minionConfig);

            return createSuccess().addField("Minion Name", minionName);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot add slave. Check if the slave is online and try again.", e);
        }
    }

    private DataResult doRemoveMinion(MinionRoot root, String minionName) {
        MinionManifest mf = new MinionManifest(root.getHive());
        MinionConfiguration minionConfig = mf.read();
        minionConfig.removeMinion(minionName);
        mf.update(minionConfig);
        return createSuccess().addField("Minion Name", minionName);
    }

    private RenderableResult doListMinions(MinionRoot r) {
        DataTable table = createDataTable();
        table.column("Name", 20).column("OS", 20).column("URI", 50);

        for (Map.Entry<String, MinionDto> entry : r.getMinions().entrySet()) {
            String name = entry.getKey();
            MinionDto details = entry.getValue();
            table.row().cell(name).cell(details.os != null ? details.os.name() : "Unknown").cell(details.remote.getUri()).build();
        }
        return table;
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

        // register the root hive as default for slaves.
        r.register(JerseyRemoteBHive.DEFAULT_NAME, root.getHive());

        srv.register(BHiveLocatorImpl.class);
        srv.register(r.binder());
        srv.register(new BHiveJacksonModule().binder());
        srv.register(MinionStatusResourceImpl.class);
        srv.register(MinionUpdateResourceImpl.class);

        if (root.getMode() != MinionMode.CENTRAL) {
            srv.register(SlaveCleanupResourceImpl.class);
            srv.register(SlaveProcessResourceImpl.class);
            srv.register(SlaveDeploymentResourceImpl.class);
            srv.register(SlaveProxyResourceImpl.class);
        }

        BroadcastingAuthenticatedWebSocket activityBc = new BroadcastingAuthenticatedWebSocket(StorageHelper::toRawBytes,
                srv.getKeyStore());
        srv.registerWebsocketApplication("/activities", activityBc);

        srv.register(new MinionCommonBinder(root, activityBc));
        srv.registerResource(r);

        return r;
    }

    private static class MinionCommonBinder extends AbstractBinder {

        private final MinionRoot root;
        private final BroadcastingAuthenticatedWebSocket activityBc;

        public MinionCommonBinder(MinionRoot root, BroadcastingAuthenticatedWebSocket activityBc) {
            this.root = root;
            this.activityBc = activityBc;
        }

        @Override
        protected void configure() {
            bind(root).to(MinionRoot.class);
            bind(root).to(Minion.class);
            bind(root.getUsers()).to(AuthService.class);
            bind(root.getState().storageMinFree).named(JerseyServer.FILE_SYSTEM_MIN_SPACE).to(Long.class);
            bind(activityBc).named(JerseyBroadcastingActivityReporter.ACTIVITY_BROADCASTER).to(JerseyEventBroadcaster.class);
        }
    }

}
