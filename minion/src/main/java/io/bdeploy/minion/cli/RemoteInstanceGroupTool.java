package io.bdeploy.minion.cli;

import java.util.SortedMap;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.cli.RemoteInstanceGroupTool.RemoteInstanceGroupConfig;

@Help("Create instance group/hive on the remote")
@CliName("remote-group")
public class RemoteInstanceGroupTool extends RemoteServiceTool<RemoteInstanceGroupConfig> {

    private static final String LIST_FORMAT = "%1$-20s %2$-10s %3$s";

    public @interface RemoteInstanceGroupConfig {

        @Help("Instance Group (and named BHive) to create. Short file-system suitable name.")
        String create();

        @Help("Instance Group display name to be set on creation.")
        String title();

        @Help("Description of the customer")
        String description();

        @Help("Storage to create the new hive in, defaults to the first available storage.")
        String storage();

        @Help("Delete the given instance group (and associated BHive). This CANNOT BE UNDONE.")
        String delete();

        @Help(value = "List existing instance groups on the remote", arg = false)
        boolean list() default false;
    }

    public RemoteInstanceGroupTool() {
        super(RemoteInstanceGroupConfig.class);
    }

    @Override
    protected void run(RemoteInstanceGroupConfig config, RemoteService svc) {
        CommonRootResource client = ResourceProvider.getResource(svc, CommonRootResource.class, null);

        if (config.create() != null) {
            helpAndFailIfMissing(config.description(), "Missing description");

            InstanceGroupConfiguration desc = new InstanceGroupConfiguration();
            desc.name = config.create();
            desc.description = config.description();
            desc.title = config.title();

            client.addInstanceGroup(desc, config.storage());
        } else if (config.list()) {
            out().println(String.format(LIST_FORMAT, "Name", "Title", "Ins. Count", "Description"));
            for (InstanceGroupConfiguration cfg : client.getInstanceGroups()) {
                SortedMap<Manifest.Key, InstanceConfiguration> ics = client.getInstanceResource(cfg.name)
                        .listInstanceConfigurations(true);
                out().println(String.format(LIST_FORMAT, cfg.name, cfg.title, ics.size(), cfg.description));
            }
        } else if (config.delete() != null) {
            // don't use out() here, really make sure the warning appears on screen.
            String confirmation = System.console().readLine(
                    "Delete %1$s? This CANNOT be undone. Type the name of the Instance Group to delete to confirm: ",
                    config.delete());

            if (confirmation != null && confirmation.equals(config.delete())) {
                client.deleteInstanceGroup(config.delete());
            } else {
                System.out.println("Aborting, no confirmation to delete");
            }
        }

    }

}
