package io.bdeploy.minion.cli;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.cli.RemoteDeploymentTool.RemoteDeployConfig;

@Help("Deploy to a remote master minion")
@CliName("remote-deployment")
public class RemoteDeploymentTool extends RemoteServiceTool<RemoteDeployConfig> {

    private static final String OUTPUT_FORMAT = "%1$-15s %2$-20s %3$-4s %4$11s %5$-25s %6$20s %7$s";

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
                out().println(String.format(OUTPUT_FORMAT, "UUID", "Name", "Tag", "Active", "Product", "Product Version",
                        "Description"));

                for (InstanceConfiguration ic : master.listInstanceConfigurations()) {
                    String uuid = ic.uuid;
                    InstanceStateRecord state = master.getInstanceState(uuid);
                    if (state.installedTags.isEmpty()) {
                        out().println(String.format(OUTPUT_FORMAT, uuid, ic.name, "-", "no-install", ic.product.getName(),
                                ic.product.getTag(), ic.description));
                    } else {
                        for (String tag : state.installedTags) {
                            boolean isActive = tag.equals(state.activeTag); // activeTag may be null.
                            out().println(String.format(OUTPUT_FORMAT, uuid, ic.name, tag, isActive ? "*" : "",
                                    ic.product.getName(), ic.product.getTag(), ic.description));
                        }
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
