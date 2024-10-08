package io.bdeploy.bhive.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.cli.ExportTool.ExportConfig;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ExportOperation;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.NonExistingOrEmptyDirPathValidator;
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;

/**
 * A tool to export a given manifest's files recursively to a target directory.
 */
@Help("Export a given manifest to a given target location")
@ToolCategory(BHiveCli.FS_TOOLS)
@CliName("export")
public class ExportTool extends ConfiguredCliTool<ExportConfig> {

    public @interface ExportConfig {

        @Help("The source BHive to export from")
        @EnvironmentFallback("BHIVE")
        @Validator({ ExistingPathValidator.class, PathOwnershipValidator.class })
        String hive();

        @Help("The target path to export to")
        @Validator(NonExistingOrEmptyDirPathValidator.class)
        String target();

        @Help("Manifest(s) to export. Format is 'name:tag'")
        String manifest();

        @Help("Parallelism - how many threads to use to export. Default: 4")
        int jobs() default 4;
    }

    public ExportTool() {
        super(ExportConfig.class);
    }

    @Override
    protected RenderableResult run(ExportConfig config) {
        helpAndFailIfMissing(config.hive(), "Missing --hive");
        helpAndFailIfMissing(config.manifest(), "Missing --manifest");

        Path targetPath = Paths.get(config.target());
        Path path = Paths.get(config.hive());

        try (BHive hive = new BHive(path.toUri(), getAuditorFactory().apply(path), getActivityReporter())) {
            hive.setParallelism(config.jobs());

            ExportOperation export = new ExportOperation().setManifest(Manifest.Key.parse(config.manifest()))
                    .setTarget(targetPath);
            hive.execute(export);
        }

        return createSuccess();
    }

}
