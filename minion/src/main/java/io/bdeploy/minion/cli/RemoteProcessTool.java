package io.bdeploy.minion.cli;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.cli.RemoteProcessTool.RemoteProcessConfig;

@Help("Deploy to a remote master minion")
@CliName("remote-process")
public class RemoteProcessTool extends RemoteServiceTool<RemoteProcessConfig> {

    public @interface RemoteProcessConfig {

        @Help("UUID of the deployment to query/control")
        String uuid();

        @Help("The name of the application to control, controls all applications for the given UUID if missing")
        String application();

        @Help("The name of the remote hive to work on")
        String target();

        @Help(value = "List process status on the remote", arg = false)
        boolean status() default false;

        @Help(value = "Start a named process.", arg = false)
        boolean start() default false;

        @Help(value = "Stop a named process.", arg = false)
        boolean stop() default false;
    }

    public RemoteProcessTool() {
        super(RemoteProcessConfig.class);
    }

    @Override
    protected void run(RemoteProcessConfig config, RemoteService svc) {
        helpAndFailIfMissing(config.uuid(), "Missing --uuid");
        helpAndFailIfMissing(config.target(), "Missing --target");

        if (!config.start() && !config.status() && !config.stop()) {
            helpAndFailIfMissing(null, "Missing --start or --stop or --status");
        }

        MasterRootResource proxy = ResourceProvider.getResource(svc, MasterRootResource.class);
        MasterNamedResource master = proxy.getNamedMaster(config.target());
        if (config.start() || config.stop()) {
            if (config.start()) {
                master.start(config.uuid(), config.application());
            } else if (config.stop()) {
                master.stop(config.uuid(), config.application());
            }
        }

        InstanceStatusDto status = master.getStatus(config.uuid());
        out().println(status.log());
    }

}
