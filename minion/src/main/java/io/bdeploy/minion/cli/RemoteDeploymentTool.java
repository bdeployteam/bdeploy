package io.bdeploy.minion.cli;

import java.util.SortedMap;
import java.util.TreeMap;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.cli.RemoteDeploymentTool.RemoteDeployConfig;

@Help("Deploy to a remote master minion")
@CliName("remote-deployment")
public class RemoteDeploymentTool extends RemoteServiceTool<RemoteDeployConfig> {

    private static final String OUTPUT_FORMAT = "%1$-15s %2$-20s %3$-4s %4$9s %5$11s %6$-25s %7$20s %8$s";

    public @interface RemoteDeployConfig {

        @Help("Target instance group name, must be created up front.")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help("UUID of the instance to manipulate")
        String uuid();

        @Help("Tag of the instance to manipulate")
        String tag();

        @Help("Product tag to update to")
        String updateTo();

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
            helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");

            MasterRootResource root = ResourceProvider.getResource(svc, MasterRootResource.class);
            MasterNamedResource master = root.getNamedMaster(config.instanceGroup());
            if (config.list()) {
                list(master);
                return;
            } else if (config.updateTo() != null) {
                helpAndFailIfMissing(config.uuid(), "Missing --uuid");

                // update the product tag...
                master.updateTo(config.uuid(), config.updateTo());

                return;
            }

            helpAndFailIfMissing(config.uuid(), "Missing --uuid");
            helpAndFailIfMissing(config.tag(), "Missing --tag");
            Manifest.Key key = new Manifest.Key(InstanceManifest.getRootName(config.uuid()), config.tag());

            if (config.install()) {
                master.install(key);
            } else if (config.activate()) {
                master.activate(key);
            } else if (config.uninstall()) {
                master.uninstall(key);
            }
        } catch (

        Exception e) {
            throw new IllegalStateException("Cannot communicate with remote", e);
        }
    }

    private void list(MasterNamedResource master) {
        out().println(String.format(OUTPUT_FORMAT, "UUID", "Name", "Tag", "Installed", "Active", "Product", "Product Version",
                "Description"));

        SortedMap<String, InstanceStateRecord> stateCache = new TreeMap<>();
        master.listInstanceConfigurations(false).entrySet().stream().sorted((a, b) -> {
            int x = a.getValue().uuid.compareTo(b.getValue().uuid);
            if (x != 0) {
                return x;
            }
            return Long.compare(Long.valueOf(b.getKey().getTag()), Long.valueOf(a.getKey().getTag()));
        }).forEachOrdered(e -> {
            String uuid = e.getValue().uuid;
            InstanceStateRecord state = stateCache.computeIfAbsent(uuid, u -> master.getInstanceState(u));
            InstanceConfiguration ic = e.getValue();

            boolean isActive = e.getKey().getTag().equals(state.activeTag); // activeTag may be null.
            boolean isInstalled = state.installedTags.contains(e.getKey().getTag());

            out().println(
                    String.format(OUTPUT_FORMAT, uuid, ic.name, e.getKey().getTag(), isInstalled ? "*" : "", isActive ? "*" : "",
                            e.getValue().product.getName(), e.getValue().product.getTag(), e.getValue().description));
        });
    }

}
