package io.bdeploy.minion.cli;

import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
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
            MasterRootResource client = ResourceProvider.getResource(svc, MasterRootResource.class);
            helpAndFailIfMissing(config.target(), "Missing --target");

            if (config.list()) {

                SortedMap<String, SortedSet<Key>> available = client.getNamedMaster(config.target()).getAvailableDeployments();
                SortedMap<String, Key> active = client.getNamedMaster(config.target()).getActiveDeployments();

                out().println(String.format("%1$-15s %2$-30s %3$-10s", "UUID", "MANIFEST", "ACTIVE"));

                for (Entry<String, SortedSet<Key>> entry : available.entrySet()) {
                    String uuid = entry.getKey();
                    Manifest.Key activeKey = active.get(uuid);

                    for (Manifest.Key k : entry.getValue()) {
                        out().println(
                                String.format("%1$-15s %2$-30s %3$-10s", uuid, k.toString(), k.equals(activeKey) ? "*" : ""));
                    }
                }
                return;
            }

            helpAndFailIfMissing(config.manifest(), "Missing --manifest");
            Manifest.Key key = Manifest.Key.parse(config.manifest());

            if (config.install()) {
                client.getNamedMaster(config.target()).install(key);
            } else if (config.activate()) {
                client.getNamedMaster(config.target()).activate(key);
            } else if (config.uninstall()) {
                client.getNamedMaster(config.target()).remove(key);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot communicate with remote", e);
        }
    }

}
