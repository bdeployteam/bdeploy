package io.bdeploy.bhive.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.LongAdder;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.cli.DiscUsageTool.DiscUsageConfig;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.bhive.op.ObjectSizeOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.ToolDefaultVerbose;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.util.FormatHelper;

/**
 * A tool to check disc usage for one or more given manifests.
 */
@Help("Calculate disc usage for given Manifest(s).")
@ToolCategory(BHiveCli.MAINTENANCE_TOOLS)
@CliName("du")
@ToolDefaultVerbose(true)
public class DiscUsageTool extends ConfiguredCliTool<DiscUsageConfig> {

    public @interface DiscUsageConfig {

        @Help("The local BHive")
        @EnvironmentFallback("BHIVE")
        @Validator({ ExistingPathValidator.class, PathOwnershipValidator.class })
        String hive();

        @Help("Manifest(s) to inspect. May appear multiple times. Format is 'name:tag'. If not present, all manifests are calculated.")
        String[] manifest() default {};

        @Help("Manifest name prefix. All manifests matching this prefix will be calculated.")
        String manifestPrefix();
    }

    public DiscUsageTool() {
        super(DiscUsageConfig.class);
    }

    @Override
    protected RenderableResult run(DiscUsageConfig config) {
        helpAndFailIfMissing(config.hive(), "Missing --hive");
        if (config.manifestPrefix() != null && config.manifest().length != 0) {
            helpAndFail("Pass either prefix or manifests, not both");
        }

        Path path = Paths.get(config.hive());
        DataTable result = createDataTable();

        result.setCaption("Disc Usage");
        result.column("Manifest Name", 100).column("Tag", 20).column("Size", 15).column("# Obj", 10).column("# Ref", 5);
        result.addFooter(
                "Note that objects may be calculated multiple times. The actual disc usage sum may be much lower than the sum of manifest size.");

        try (BHive hive = new BHive(path.toUri(), getAuditorFactory().apply(path), new ActivityReporter.Null())) {
            SortedSet<Manifest.Key> keys = new TreeSet<>();

            if (config.manifestPrefix() != null) {
                keys.addAll(hive.execute(new ManifestListOperation().setManifestName(config.manifestPrefix()))); // only with prefix
            } else if (config.manifest().length > 0) {
                for (String k : config.manifest()) {
                    keys.add(Manifest.Key.parse(k));
                }
            } else {
                keys.addAll(hive.execute(new ManifestListOperation())); // all
            }

            try (Activity calculating = getActivityReporter().start("Calculate Disc Usage", keys.size())) {
                for (Manifest.Key k : keys) {
                    calculateUsage(hive, k, result);
                    calculating.worked(1);
                }
            }
        }

        return result;
    }

    private void calculateUsage(BHive hive, Manifest.Key key, DataTable result) {
        LongAdder refs = new LongAdder();

        // find all objects.
        Set<ObjectId> objects = hive.execute(new ObjectListOperation().addManifest(key));

        // calculate sizes.
        Long size = hive.execute(new ObjectSizeOperation().addObject(objects));

        result.row().cell(key.getName()).cell(key.getTag()).cell(FormatHelper.formatFileSize(size)).cell(objects.size())
                .cell(refs.sum()).build();
    }

}
