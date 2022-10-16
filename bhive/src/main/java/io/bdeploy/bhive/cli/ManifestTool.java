package io.bdeploy.bhive.cli;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.SortedMap;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.cli.ManifestTool.ManifestConfig;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.CopyOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import jakarta.ws.rs.core.UriBuilder;

/**
 * A tool to list and manage (delete, export) manifests in a hive.
 */
@Help("Query and manipulate manifests in the given BHive")
@ToolCategory(BHiveCli.MAINTENANCE_TOOLS)
@CliName("manifest")
public class ManifestTool extends RemoteServiceTool<ManifestConfig> {

    public @interface ManifestConfig {

        @Help("The BHive to use. Alternatively use --remote.")
        @EnvironmentFallback("BHIVE")
        @Validator({ ExistingPathValidator.class, PathOwnershipValidator.class })
        String hive();

        @Help(value = "List available manifests", arg = false)
        boolean list() default false;

        @Help(value = "Delete a given manifest", arg = false)
        boolean delete() default false;

        @Help("The name of the hive on the remote server if going remote")
        String source();

        @Help("Manifest(s) to manipulate/list. Format is 'name:tag'. Name without tag is supported to list tags of a given name.")
        String manifest() default "";

        @Help("Path to a ZIP file where the manifest and all its dependencies should be saved to.")
        String saveTo();
    }

    public ManifestTool() {
        super(ManifestConfig.class);
    }

    @Override
    protected RenderableResult run(ManifestConfig config, @RemoteOptional RemoteService svc) {
        if (svc == null) {
            helpAndFailIfMissing(config.hive(), "Missing --hive");
        }

        if (!config.list() && !config.delete() && config.saveTo() == null) {
            return createNoOp();
        }

        if (config.hive() != null) {
            return runOnLocalHive(config);
        } else {
            return runOnRemoteHive(config, svc);
        }
    }

    private RenderableResult runOnRemoteHive(ManifestConfig config, RemoteService svc) {
        if (config.delete()) {
            throw new UnsupportedOperationException("Remote manifest deletion not supported.");
        }
        if (config.saveTo() != null) {
            throw new UnsupportedOperationException("Remote manifest saving not supported.");
        }

        try (RemoteBHive rh = RemoteBHive.forService(svc, config.source(), getActivityReporter())) {
            if (config.list()) {
                SortedMap<Manifest.Key, ObjectId> mfs = rh.getManifestInventory();
                if (mfs.isEmpty()) {
                    return createResultWithErrorMessage("No manifests found");
                } else {
                    DataTable table = createDataTable();
                    table.column("Key", 50).column("Root", 40);
                    mfs.entrySet().stream().filter(e -> matches(e.getKey(), config))
                            .forEach(e -> table.row().cell(e.getKey()).cell(e.getValue()).build());
                    return table;
                }
            }
        }

        return createSuccess();
    }

    private RenderableResult runOnLocalHive(ManifestConfig config) {
        Path path = Paths.get(config.hive());
        try (BHive hive = new BHive(path.toUri(), getAuditorFactory().apply(path), getActivityReporter())) {

            if (config.list()) {
                Set<Manifest.Key> manifests = hive.execute(new ManifestListOperation());
                if (manifests.isEmpty()) {
                    return createResultWithErrorMessage("No manifests found");
                } else {
                    DataTable table = createDataTable();
                    table.column("Key", 50).column("Root", 40);
                    manifests.stream().filter(x -> matches(x, config)).forEach(e -> {
                        Manifest m = hive.execute(new ManifestLoadOperation().setManifest(e));
                        table.row().cell(e).cell(m.getRoot().toString()).build();
                    });
                    return table;
                }
            }

            if (config.delete()) {
                helpAndFailIfMissing(config.manifest(), "Missing --manifest");
                Key manifest = Manifest.Key.parse(config.manifest());
                hive.execute(new ManifestDeleteOperation().setToDelete(manifest));

                return createSuccess().addField("Deleted", manifest.toString());
            }

            if (config.saveTo() != null) {
                return doSaveTo(config, hive);
            }

            return createSuccess();
        }

    }

    private DataResult doSaveTo(ManifestConfig config, BHive hive) {
        helpAndFailIfMissing(config.manifest(), "Missing --manifest");
        Key manifest = Manifest.Key.parse(config.manifest());
        Path tmpFile = null;
        try {
            tmpFile = Files.createTempDirectory("bdeploy-");

            // Determine required objects
            Set<ObjectId> objectIds = hive.execute(new ObjectListOperation().addManifest(manifest));

            // Copy objects into the target hive
            URI targetUri = UriBuilder.fromUri("jar:" + Paths.get(config.saveTo()).toUri()).build();
            try (BHive zipHive = new BHive(targetUri, null, new ActivityReporter.Null())) {
                CopyOperation op = new CopyOperation().setDestinationHive(zipHive);
                op.addManifest(manifest);
                objectIds.forEach(op::addObject);

                TransferStatistics stats = hive.execute(op);
                DataResult result = createSuccess();
                result.addField("Number of Manifests", stats.sumManifests);
                result.addField("Number of Objects", stats.sumMissingObjects);
                result.addField("Duration", FormatHelper.formatDuration(stats.duration));
                return result;
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save manifest", ex);
        } finally {
            if (tmpFile != null) {
                PathHelper.deleteRecursive(tmpFile);
            }
        }
    }

    private boolean matches(Manifest.Key x, ManifestConfig config) {
        if (config.manifest().isEmpty()) {
            return true;
        }

        if (!config.manifest().contains(":") && !x.getName().equals(config.manifest())) {
            return false;
        }
        return Manifest.Key.parse(config.manifest()).equals(x);
    }

}