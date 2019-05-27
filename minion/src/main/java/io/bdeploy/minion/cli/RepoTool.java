package io.bdeploy.minion.cli;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.cli.RepoTool.RepoConfig;

/**
 * Manages storage locations.
 */
@Help("Manage software repositories for the minion.")
@CliName("repo")
public class RepoTool extends RemoteServiceTool<RepoConfig> {

    public @interface RepoConfig {

        @Help("Adds a software repository with the given name.")
        String add();

        @Help("The description to set for the software repo.")
        String description();

        @Help("The storage to create the repo at. Defaults to the first storage location.")
        String storage();

        @Help(value = "List existing software repositories", arg = false)
        boolean list() default false;
    }

    public RepoTool() {
        super(RepoConfig.class);
    }

    @Override
    protected void run(RepoConfig config, RemoteService svc) {
        MasterRootResource client = ResourceProvider.getResource(svc, MasterRootResource.class);
        if (config.add() != null) {
            helpAndFailIfMissing(config.description(), "Missing --description");

            SoftwareRepositoryConfiguration meta = new SoftwareRepositoryConfiguration();
            meta.name = config.add();
            meta.description = config.description();

            client.addSoftwareRepository(meta, config.storage());
        } else if (config.list()) {
            for (SoftwareRepositoryConfiguration cfg : client.getSoftwareRepositories()) {
                out().println(String.format("%1$-30s %2$s", cfg.name, cfg.description));
            }
        } else {
            helpAndFail("Missing either --add or --list");
        }
    }

}
