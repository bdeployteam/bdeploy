package io.bdeploy.minion.cli;

import static io.bdeploy.common.util.RuntimeAssert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Map;

import org.glassfish.hk2.utilities.binding.AbstractBinder;

import io.bdeploy.bhive.remote.jersey.BHiveJacksonModule;
import io.bdeploy.bhive.remote.jersey.BHiveLocatorImpl;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.jersey.RegistrationTarget;
import io.bdeploy.jersey.audit.RollingFileAuditor;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.MinionState;
import io.bdeploy.minion.cli.SlaveTool.SlaveConfig;
import io.bdeploy.minion.remote.jersey.JerseyAwareMinionUpdateManager;
import io.bdeploy.minion.remote.jersey.MinionStatusResourceImpl;
import io.bdeploy.minion.remote.jersey.MinionUpdateResourceImpl;
import io.bdeploy.minion.remote.jersey.SlaveCleanupResourceImpl;
import io.bdeploy.minion.remote.jersey.SlaveDeploymentResourceImpl;
import io.bdeploy.minion.remote.jersey.SlaveProcessResourceImpl;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.Minion;

/**
 * Manages slaves.
 */
@Help("Manage minions on a master, or start a non-master minion.")
@CliName("slave")
public class SlaveTool extends RemoteServiceTool<SlaveConfig> {

    public @interface SlaveConfig {

        @Help("Root directory for the master minion. The minion will put all required things here.")
        @EnvironmentFallback("BDEPLOY_ROOT")
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
    protected void run(SlaveConfig config, @RemoteOptional RemoteService svc) {
        helpAndFailIfMissing(config.root(), "Missing --root");
        out().println("Starting slave...");

        ActivityReporter.Delegating delegate = new ActivityReporter.Delegating();
        delegate.setDelegate(getActivityReporter());
        try (MinionRoot r = new MinionRoot(Paths.get(config.root()), delegate)) {
            if (config.updateDir() != null) {
                Path upd = Paths.get(config.updateDir());
                r.setUpdateDir(upd);
            }
            MinionState state = r.getState();

            if (config.list()) {
                for (Map.Entry<String, RemoteService> entry : state.minions.entrySet()) {
                    out().println(String.format("%1$-30s %2$-40s", entry.getKey(), entry.getValue().getUri()));
                }
            } else if (config.add() != null || config.remove() != null) {
                if (config.add() != null) {
                    helpAndFailIfMissing(svc, "Missing --remote");
                    try {
                        assertTrue(svc.getUri().getScheme().toLowerCase().equals("https"), "Only HTTPS slaves supported");
                        state.minions.put(config.add(), svc);
                    } catch (Exception e) {
                        throw new IllegalStateException("Cannot add slave", e);
                    }
                } else if (config.remove() != null) {
                    state.minions.remove(config.remove());
                }

                r.setState(state);
            } else {
                // run the slave :)
                int port = state.port;
                try {
                    SecurityHelper sh = SecurityHelper.getInstance();
                    KeyStore ks = sh.loadPrivateKeyStore(state.keystorePath, state.keystorePass);
                    try (JerseyServer srv = new JerseyServer(port, ks, state.keystorePass)) {
                        srv.setAuditor(new RollingFileAuditor(r.getAuditLogDir()));
                        r.setUpdateManager(new JerseyAwareMinionUpdateManager(srv));

                        delegate.setDelegate(srv.getSseActivityReporter());
                        registerCommonResources(srv, r, srv.getSseActivityReporter());
                        r.setupServerTasks(false);

                        srv.start();
                        srv.join();
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot run server on " + port, e);
                }
            }
        }
    }

    public static BHiveRegistry registerCommonResources(RegistrationTarget srv, MinionRoot root, ActivityReporter reporter) {
        BHiveRegistry r = new BHiveRegistry(reporter);

        // register the root hive as default for slaves.
        r.register(JerseyRemoteBHive.DEFAULT_NAME, root.getHive());

        srv.register(BHiveLocatorImpl.class);
        srv.register(r.binder());
        srv.register(new BHiveJacksonModule().binder());
        srv.register(MinionStatusResourceImpl.class);
        srv.register(MinionUpdateResourceImpl.class);
        srv.register(SlaveCleanupResourceImpl.class);
        srv.register(SlaveProcessResourceImpl.class);
        srv.register(SlaveDeploymentResourceImpl.class);
        srv.register(new MinionCommonBinder(root));
        srv.registerResource(r);

        return r;
    }

    private static class MinionCommonBinder extends AbstractBinder {

        private final MinionRoot root;

        public MinionCommonBinder(MinionRoot root) {
            this.root = root;
        }

        @Override
        protected void configure() {
            bind(root).to(MinionRoot.class);
            bind(root).to(Minion.class);
            bind(root.getUsers()).to(AuthService.class);
        }
    }

}
