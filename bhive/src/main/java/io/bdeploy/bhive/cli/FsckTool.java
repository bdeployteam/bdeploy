package io.bdeploy.bhive.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.cli.FsckTool.FsckConfig;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.objects.view.DamagedObjectView;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.op.FsckOperation;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.RenderableResult;

/**
 * A tool to check consistency of manifests and objects.
 * <p>
 * By default prints a report of found defects. Give --repair to remove defects. This will bring the {@link BHive} back to a
 * technically consistent state, but removes inconsistent manifests, so they need to be re-pushed if required.
 */
@Help("Check manifest and object consistency on a BHive instance. Broken objects will be removed.")
@ToolCategory(BHiveCli.MAINTENANCE_TOOLS)
@CliName("fsck")
public class FsckTool extends ConfiguredCliTool<FsckConfig> {

    public @interface FsckConfig {

        @Help("The local BHive")
        @EnvironmentFallback("BHIVE")
        @Validator({ ExistingPathValidator.class, PathOwnershipValidator.class })
        String hive();

        @Help("Manifest(s) to check. May appear multiple times. Format is 'name:tag'. If not present, all manifests are checked.")
        String[] manifest() default {};

        @Help(value = "Repair the Hive by removing any damaged objects", arg = false)
        boolean repair() default false;
    }

    public FsckTool() {
        super(FsckConfig.class);
    }

    @Override
    protected RenderableResult run(FsckConfig config) {
        helpAndFailIfMissing(config.hive(), "Missing --hive");

        Path path = Paths.get(config.hive());

        try (BHive hive = new BHive(path.toUri(), getAuditorFactory().apply(path), getActivityReporter())) {
            FsckOperation op = new FsckOperation().setRepair(config.repair());
            Arrays.stream(config.manifest()).map(Manifest.Key::parse).forEach(op::addManifest);

            Set<ElementView> broken = hive.execute(op);

            if (broken.isEmpty()) {
                return createSuccess();
            }

            DataResult result = createResultWithMessage("Found " + broken.size() + " damaged objects!");
            for (ElementView ele : broken) {
                result.addField(ele.getElementId().toString(),
                        (ele instanceof DamagedObjectView ? (((DamagedObjectView) ele).getType() + " ") : "")
                                + ele.getPathString());
            }
            return result;
        }
    }

}
