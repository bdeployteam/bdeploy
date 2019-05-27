package io.bdeploy.bhive.cli;

import java.nio.file.Paths;
import java.util.SortedMap;
import java.util.SortedSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.cli.ManifestTool.ManifestConfig;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.cli.RemoteServiceTool;

/**
 * A tool to list and manage (delete) manifests in a hive.
 */
@Help("Query and manipulate manifests in the given BHive")
@CliName("manifest")
public class ManifestTool extends RemoteServiceTool<ManifestConfig> {

    public @interface ManifestConfig {

        @Help("The BHive to use. Alternatively use --remote.")
        @EnvironmentFallback("BHIVE")
        String hive();

        @Help(value = "List available manifests", arg = false)
        boolean list() default false;

        @Help(value = "Delete a given manifest", arg = false)
        boolean delete() default false;

        @Help("The name of the hive on the remote server if going remote")
        String source();

        @Help("Manifest(s) to manipulate/list. Format is 'name:tag'. Name without tag is supported to list tags of a given name.")
        String manifest() default "";
    }

    public ManifestTool() {
        super(ManifestConfig.class);
    }

    @Override
    protected void run(ManifestConfig config, @RemoteOptional RemoteService svc) {
        if (svc == null) {
            helpAndFailIfMissing(config.hive(), "Missing --hive");
        }

        if (!config.list() && !config.delete()) {
            helpAndFail("Nothing to do, please give more options");
        }

        if (config.hive() != null) {
            runOnLocalHive(config);
        } else {
            runOnRemoteHive(config, svc);
        }
    }

    private void runOnRemoteHive(ManifestConfig config, RemoteService svc) {
        if (config.delete()) {
            throw new UnsupportedOperationException("Remote manifest deletion not supported.");
        }

        try (RemoteBHive rh = RemoteBHive.forService(svc, config.source(), getActivityReporter())) {
            if (config.list()) {
                SortedMap<Manifest.Key, ObjectId> mfs = rh.getManifestInventory();
                if (mfs.isEmpty()) {
                    out().println("No manifests found");
                } else {
                    mfs.entrySet().stream().filter(e -> matches(e.getKey(), config)).forEach(e -> {
                        out().println(String.format("%1$-70s %2$s", e.getKey(), e.getValue()));
                    });
                }
            }
        }
    }

    private void runOnLocalHive(ManifestConfig config) {
        try (BHive hive = new BHive(Paths.get(config.hive()).toUri(), getActivityReporter())) {

            if (config.list()) {
                SortedSet<Manifest.Key> manifests = hive.execute(new ManifestListOperation());
                if (manifests.isEmpty()) {
                    out().println("No manifests found");
                } else {
                    manifests.stream().filter(x -> matches(x, config)).forEach((e) -> {
                        Manifest m = hive.execute(new ManifestLoadOperation().setManifest(e));
                        out().println(String.format("%1$-70s %2$s", e, m.getRoot()));
                    });
                }
            }

            if (config.delete()) {
                helpAndFailIfMissing(config.manifest(), "Missing --manifest");
                hive.execute(new ManifestDeleteOperation().setToDelete(Manifest.Key.parse(config.manifest())));
            }

        }
    }

    private boolean matches(Manifest.Key x, ManifestConfig config) {
        if (config.manifest().isEmpty()) {
            return true;
        }

        if (!config.manifest().contains(":")) {
            if (!x.getName().equals(config.manifest())) {
                return false;
            }
        }
        return Manifest.Key.parse(config.manifest()).equals(x);
    }

}