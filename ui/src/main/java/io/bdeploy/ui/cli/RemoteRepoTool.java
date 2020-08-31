package io.bdeploy.ui.cli;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.SoftwareRepositoryResource;
import io.bdeploy.ui.cli.RemoteRepoTool.RepoConfig;

/**
 * Manages storage locations.
 */
@Help("Manage software repositories for the minion.")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-repo")
public class RemoteRepoTool extends RemoteServiceTool<RepoConfig> {

    public @interface RepoConfig {

        @Help("Adds a software repository with the given name.")
        String add();

        @Help("The description to set for the software repo.")
        String description();

        @Help(value = "List existing software repositories", arg = false)
        boolean list() default false;
    }

    public RemoteRepoTool() {
        super(RepoConfig.class);
    }

    @Override
    protected RenderableResult run(RepoConfig config, RemoteService svc) {
        SoftwareRepositoryResource client = ResourceProvider.getResource(svc, SoftwareRepositoryResource.class,
                getLocalContext());
        if (config.add() != null) {
            helpAndFailIfMissing(config.description(), "Missing --description");

            SoftwareRepositoryConfiguration meta = new SoftwareRepositoryConfiguration();
            meta.name = config.add();
            meta.description = config.description();

            client.create(meta);
            return createSuccess();
        } else if (config.list()) {
            DataTable table = createDataTable();
            table.setCaption("Software Repositories on " + svc.getUri());
            table.column("Name", 30).column("Description", 60);
            for (SoftwareRepositoryConfiguration cfg : client.list()) {
                table.row().cell(cfg.name).cell(cfg.description).build();
            }
            return table;
        } else {
            return createNoOp();
        }
    }

}
