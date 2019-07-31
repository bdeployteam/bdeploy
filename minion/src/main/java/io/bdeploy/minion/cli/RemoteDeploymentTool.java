package io.bdeploy.minion.cli;

import java.util.SortedMap;
import java.util.SortedSet;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.cli.RemoteDeploymentTool.RemoteDeployConfig;

@Help("Deploy to a remote master minion")
@CliName("remote-deployment")
public class RemoteDeploymentTool extends RemoteServiceTool<RemoteDeployConfig> {

    public @interface RemoteDeployConfig {

        @Help("Target hive name, must be created up front.")
        @EnvironmentFallback("REMOTE_BHIVE")
        String target();

        @Help("Name of the manifest to deploy")
        String manifest();

        @Help(value = "List deployments on the remote", arg = false)
        boolean list() default false;

        @Help(value = "Install the given instance version on the remote", arg = false)
        boolean install() default false;

        @Help(value = "Activate an installed version on the remote. Running processes must be stopped before this!", arg = false)
        boolean activate() default false;

        @Help(value = "Uninstall an instance version from the remote", arg = false)
        boolean uninstall() default false;
    }

    public RemoteDeploymentTool() {
        super(RemoteDeployConfig.class);
    }

    @Override
    protected void run(RemoteDeployConfig config, RemoteService svc) {
        try {
            helpAndFailIfMissing(config.target(), "Missing --target");

            MasterRootResource root = ResourceProvider.getResource(svc, MasterRootResource.class);
            MasterNamedResource master = root.getNamedMaster(config.target());
            if (config.list()) {
                out().println(String.format("%1$-15s %2$-30s %3$-10s", "UUID", "MANIFEST", "ACTIVE"));

                SortedMap<String, Key> active = master.getActiveDeployments();
                for (InstanceConfiguration ic : master.listInstanceConfigurations()) {
                    String uuid = ic.uuid;
                    SortedSet<Key> deployments = master.getAvailableDeploymentsOfInstance(uuid);
                    for (Manifest.Key k : deployments) {
                        Manifest.Key activeKey = active.get(uuid);
                        boolean isActive = k.equals(activeKey);
                        out().println(String.format("%1$-15s %2$-30s %3$-10s", uuid, k, isActive ? "*" : ""));
                    }
                }
                return;
            }

            helpAndFailIfMissing(config.manifest(), "Missing --manifest");
            Manifest.Key key = Manifest.Key.parse(config.manifest());

            if (config.install()) {
                master.install(key);
            } else if (config.activate()) {
                master.activate(key);
            } else if (config.uninstall()) {
                master.remove(key);
            }
        } catch (

        Exception e) {
            throw new IllegalStateException("Cannot communicate with remote", e);
        }
    }

}
