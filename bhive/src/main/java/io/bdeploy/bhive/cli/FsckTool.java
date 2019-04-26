package io.bdeploy.bhive.cli;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.cli.FsckTool.FsckConfig;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.op.FsckOperation;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;

/**
 * A tool to check consistency of manifests and objects.
 * <p>
 * By default prints a report of found defects. Give --repair to remove defects. This will bring the {@link BHive} back to a
 * technically consistent state, but removes inconsistent manifests, so they need to be re-pushed if required.
 */
@Help("Check manifest and object consistency on a BHive instance. Broken objects will be removed.")
@CliName("fsck")
public class FsckTool extends ConfiguredCliTool<FsckConfig> {

    public @interface FsckConfig {

        @Help("The local BHive")
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
    protected void run(FsckConfig config) {
        helpAndFailIfMissing(config.hive(), "Missing --hive");

        try (BHive hive = new BHive(Paths.get(config.hive()).toUri(), getActivityReporter())) {
            FsckOperation op = new FsckOperation().setRepair(config.repair());
            Arrays.stream(config.manifest()).map(Manifest.Key::parse).forEach(op::addManifest);

            List<ElementView> broken = hive.execute(op);

            broken.stream().forEach(x -> out().println("PROBLEMATIC OBJECT: " + x));
            if (broken.isEmpty()) {
                out().println("Check OK");
            }
        }
    }

}
