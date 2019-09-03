package io.bdeploy.minion.cli;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.cli.RemoteInstanceGroupTool.RemoteInstanceGroupConfig;

@Help("Create instance group/hive on the remote")
@CliName("remote-group")
public class RemoteInstanceGroupTool extends RemoteServiceTool<RemoteInstanceGroupConfig> {

    public @interface RemoteInstanceGroupConfig {

        @Help("Instance Group (and named hive) to create. Short file-system suitable name.")
        String create();

        @Help("Description of the customer")
        String description();

        @Help("Storage to create the new hive in, defaults to the first available storage.")
        String storage();

        @Help(value = "List existing instance groups on the remote", arg = false)
        boolean list() default false;
    }

    public RemoteInstanceGroupTool() {
        super(RemoteInstanceGroupConfig.class);
    }

    @Override
    protected void run(RemoteInstanceGroupConfig config, RemoteService svc) {
        MasterRootResource client = ResourceProvider.getResource(svc, MasterRootResource.class);

        if (config.create() != null) {
            helpAndFailIfMissing(config.description(), "Missing description");

            InstanceGroupConfiguration desc = new InstanceGroupConfiguration();
            desc.name = config.create();
            desc.description = config.description();

            client.addInstanceGroup(desc, config.storage());
        } else if (config.list()) {
            for (InstanceGroupConfiguration cfg : client.getInstanceGroups()) {
                out().println(String.format("%1$-30s %2$s", cfg.name, cfg.description));
            }
        }

    }

}
