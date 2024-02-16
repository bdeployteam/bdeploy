package io.bdeploy.bhive.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHivePoolOrganizer;
import io.bdeploy.bhive.cli.PoolTool.PoolConfig;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.op.FsckOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.audit.NullAuditor;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.jersey.actions.ActionService;

@Help("Configures pooling on a given BHive.")
@ToolCategory(BHiveCli.SERVER_TOOLS)
@CliName("pool")
public class PoolTool extends ConfiguredCliTool<PoolConfig> {

    public @interface PoolConfig {

        @Help("The target BHive to configure")
        @EnvironmentFallback("BHIVE")
        @Validator({ ExistingPathValidator.class, PathOwnershipValidator.class })
        String hive();

        @Help("Path to the pool directory to use. May be an existing pool or non-existant.")
        String pool();

        @Help(value = "Force resetting the pool directory. Attention: the new pool directory MUST contain all required objects from the previous pool.",
              arg = false)
        boolean force() default false;

        @Help(value = "If pooling is enabled on this BHive, disable it and copy all required objects from the pool to the local storage",
              arg = false)
        boolean unpool() default false;

        @Help("A list of BHives whose pools should be updated. ATTENTION: all BHives using a certain pool must be given, as unreferenced objects will be deleted from the pool.")
        String[] reorganize();

        @Help("The threshold of how many times an object must be equal in pooled hives before being moved to the pool.")
        int usageThreshold() default 2;
    }

    public PoolTool() {
        super(PoolConfig.class);
    }

    @Override
    protected RenderableResult run(PoolConfig config) {
        if (config.pool() != null) {
            helpAndFailIfMissing(config.hive(), "Missing --hive");
            Path root = Paths.get(config.hive());

            out().println("WARNING: setting a BHives pool requires restarting of any existing application");
            out().println("accessing this BHive, otherwise pooling settings will not be respected.");

            try (BHive hive = new BHive(root.toUri(), getAuditorFactory().apply(root), getActivityReporter())) {
                Path pool = Paths.get(config.pool());

                if (!PathHelper.exists(pool)) {
                    PathHelper.mkdirs(pool);
                }

                Path old = hive.getPoolPath();
                hive.enablePooling(pool, config.force());

                if (config.force() && old != null) {
                    out().println("Force was given and a pool was set already, forcing full consistency check with new pool.");

                    Set<ElementView> problems = hive.execute(new FsckOperation());

                    if (!problems.isEmpty()) {
                        out().println("Consistency check failed, cannot move to new pool, reverting to existing pool!");
                        hive.enablePooling(old, true);
                    } else {
                        out().println("Consistency check OK, moving to new pool.");
                    }
                }
            }
        } else if (config.unpool()) {
            helpAndFailIfMissing(config.hive(), "Missing --hive");
            Path root = Paths.get(config.hive());

            try (BHive hive = new BHive(root.toUri(), getAuditorFactory().apply(root), getActivityReporter())) {
                hive.disablePooling();
            }
        } else if (config.reorganize() != null && config.reorganize().length > 0) {
            if (config.reorganize().length < 2) {
                return createResultWithErrorMessage("At least two hives must be given to reorganize");
            }
            BHiveRegistry reg = new BHiveRegistry(getActivityReporter(), null);
            for (String hivePath : config.reorganize()) {
                Path path = Paths.get(hivePath);
                reg.register(hivePath, new BHive(path.toUri(), getAuditorFactory().apply(path), getActivityReporter()));
            }

            BHivePoolOrganizer.reorganizeAll(reg, config.usageThreshold(), new ActionService(null, new NullAuditor()));
        } else {
            return createNoOp();
        }

        return createSuccess();
    }

}
