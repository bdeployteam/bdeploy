package io.bdeploy.ui.cli;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.cli.RemoteDeploymentTool.RemoteDeployConfig;

@Help("Install, activate and uninstall on a remote server")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-deployment")
public class RemoteDeploymentTool extends RemoteServiceTool<RemoteDeployConfig> {

    public @interface RemoteDeployConfig {

        @Help("Target instance group name.")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help("ID of the instance to manipulate or list")
        String uuid();

        @Help("Version of the instance to manipulate or list")
        String version();

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
    protected RenderableResult run(RemoteDeployConfig config, RemoteService svc) {
        helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");
        helpAndFailIfMissing(config.uuid(), "Missing --uuid");
        helpAndFailIfMissing(config.version(), "Missing --version");

        InstanceResource ir = ResourceProvider.getResource(svc, InstanceGroupResource.class, getLocalContext())
                .getInstanceResource(config.instanceGroup());

        if (config.install()) {
            ir.install(config.uuid(), config.version());
        } else if (config.activate()) {
            ir.activate(config.uuid(), config.version(), false);
        } else if (config.uninstall()) {
            ir.uninstall(config.uuid(), config.version());
        } else {
            helpAndFail("ERROR: Missing --install, --activate or --uninstall");
        }
        return createSuccess();
    }
}
