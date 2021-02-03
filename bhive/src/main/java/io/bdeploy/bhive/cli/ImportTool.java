package io.bdeploy.bhive.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.cli.ImportTool.ImportConfig;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;

/**
 * Import a source directory recursively into a hive and create a manifest for
 * it using the given values.
 */
@Help("Imports a source directory into a BHive")
@ToolCategory(BHiveCli.FS_TOOLS)
@CliName("import")
public class ImportTool extends ConfiguredCliTool<ImportConfig> {

    public @interface ImportConfig {

        @Help("The path to the directory to import")
        @Validator(ExistingPathValidator.class)
        String source();

        @Help("The target BHive to import into")
        @EnvironmentFallback("BHIVE")
        @Validator({ ExistingPathValidator.class, PathOwnershipValidator.class })
        String hive();

        @Help("Manifest(s) to create. Format is 'name:tag'")
        String manifest();

        @Help("Can appear multiple times. ':' separated key value pair of label to add")
        String[] label() default {};

        @Help("Parallelism - how many threads to use to import. Default: 4")
        int jobs() default 4;
    }

    public ImportTool() {
        super(ImportConfig.class);
    }

    @Override
    protected RenderableResult run(ImportConfig config) {
        helpAndFailIfMissing(config.source(), "Missing --source");
        helpAndFailIfMissing(config.hive(), "Missing --hive");
        helpAndFailIfMissing(config.manifest(), "Missing --manifest");

        Path source = Paths.get(config.source());
        Path target = Paths.get(config.hive());

        if (!Files.isDirectory(source)) {
            helpAndFail("Source path must be a directory (" + source + ")");
        }

        Map<String, String> labels = new TreeMap<>();
        for (String lbl : config.label()) {
            if (!lbl.contains(":")) {
                helpAndFail("Label must use ':' as key/value separator (" + lbl + ")");
            }
            int idefix = lbl.indexOf(':');
            String k = lbl.substring(0, idefix);
            String v = lbl.substring(idefix + 1);

            labels.put(k, v);
        }

        try (BHive hive = new BHive(target.toUri(), getActivityReporter()); Transaction t = hive.getTransactions().begin()) {
            hive.setParallelism(config.jobs());

            ImportOperation op = new ImportOperation().setSourcePath(source).setManifest(Manifest.Key.parse(config.manifest()));
            labels.forEach(op::addLabel);
            Manifest.Key result = hive.execute(op);

            return createSuccess().addField("Key", result.toString());
        }
    }

}
