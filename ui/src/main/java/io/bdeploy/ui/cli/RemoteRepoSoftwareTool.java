package io.bdeploy.ui.cli;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.SoftwareRepositoryResource;
import io.bdeploy.ui.cli.RemoteRepoSoftwareTool.SoftwareConfig;

/**
 * Manages storage locations.
 */
@Help("View contents of software repositories.")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-repo-software")
public class RemoteRepoSoftwareTool extends RemoteServiceTool<SoftwareConfig> {

    public @interface SoftwareConfig {

        @Help("The software repository to query.")
        String repo();

        @Help(value = "List existing software in the given repository. To list products, use the remote-product tool.",
              arg = false)
        boolean list() default false;
    }

    public RemoteRepoSoftwareTool() {
        super(SoftwareConfig.class);
    }

    @Override
    protected RenderableResult run(SoftwareConfig config, RemoteService svc) {
        helpAndFailIfMissing(config.repo(), "--repo missing");

        SoftwareRepositoryResource client = ResourceProvider.getResource(svc, SoftwareRepositoryResource.class,
                getLocalContext());

        if (config.list()) {
            DataTable table = createDataTable();
            table.setCaption("Software in " + config.repo() + " on " + svc.getUri());
            table.column("Key", 30).column("Version", 20);
            for (Key cfg : client.getSoftwareResource(config.repo()).list(false, true)) {
                table.row().cell(cfg.getName()).cell(cfg.getTag()).build();
            }
            return table;
        } else {
            return createNoOp();
        }
    }

}
