package io.bdeploy.minion.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.RepoTool.RepoConfig;

/**
 * Manages storage locations.
 */
@Help("Manage software repositories for the minion.")
@CliName("repo")
public class RepoTool extends RemoteServiceTool<RepoConfig> {

    public @interface RepoConfig {

        @Help("Root directory for the master minion.")
        String root();

        @Help("Adds a software repository with the given name.")
        String add();

        @Help("The description to set for the software repo.")
        String description();

        @Help("The storage to create the repo at.")
        String storage();

        @Help(value = "List existing software repositories", arg = false)
        boolean list() default false;
    }

    public RepoTool() {
        super(RepoConfig.class);
    }

    @Override
    protected void run(RepoConfig config, @RemoteOptional RemoteService svc) {
        helpAndFailIfMissing(config.root(), "Missing --root");

        try (MinionRoot root = new MinionRoot(Paths.get(config.root()), getActivityReporter());
                BHiveRegistry reg = new BHiveRegistry(getActivityReporter())) {

            root.getStorageLocations().forEach(reg::scanLocation);

            if (config.add() != null) {
                helpAndFailIfMissing(config.description(), "Missing --description");
                helpAndFailIfMissing(config.storage(), "Missing --storage");

                Path storage = Paths.get(config.storage());
                if (!root.getStorageLocations().contains(storage)) {
                    throw new IllegalStateException("Unknown storage: " + storage);
                }

                if (reg.get(config.add()) != null) {
                    throw new IllegalStateException("BHive with name '" + config.add() + "' already exists.");
                }

                SoftwareRepositoryConfiguration meta = new SoftwareRepositoryConfiguration();
                meta.name = config.add();
                meta.description = config.description();

                BHive h = new BHive(storage.resolve(config.add()).toUri(), getActivityReporter());
                new SoftwareRepositoryManifest(h).update(meta);
                reg.register(config.add(), h);
            } else if (config.list()) {
                for (Map.Entry<String, BHive> entry : reg.getAll().entrySet()) {
                    SoftwareRepositoryConfiguration cfg = new SoftwareRepositoryManifest(entry.getValue()).read();
                    if (cfg != null) {
                        out().println(String.format("%1$-30s %2$s", entry.getKey(), cfg.description));
                    }
                }
            }
        }

    }

}
