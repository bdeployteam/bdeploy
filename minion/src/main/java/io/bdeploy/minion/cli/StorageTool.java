package io.bdeploy.minion.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.MinionRootValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.jersey.audit.AuditRecord;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.StorageTool.StorageConfig;

/**
 * Manages storage locations.
 */
@Help("Manage storage locations for the minion.")
@CliName("storage")
public class StorageTool extends ConfiguredCliTool<StorageConfig> {

    public @interface StorageConfig {

        @Help("Root directory for the master minion. The minion will put all required things here.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator(MinionRootValidator.class)
        String root();

        @Help("Adds a storage location at the given path.")
        String add();

        @Help("Removes a storage location with the given path.")
        String remove();

        @Help(value = "When given, list all storage locations.", arg = false)
        boolean list() default false;
    }

    public StorageTool() {
        super(StorageConfig.class);
    }

    @Override
    protected void run(StorageConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");

        try (MinionRoot r = new MinionRoot(Paths.get(config.root()), getActivityReporter())) {
            List<Path> original = r.getStorageLocations();
            if (config.add() != null) {
                Path p = Paths.get(config.add());
                if (original.contains(p)) {
                    out().println(p + " already registered");
                    return;
                }
                PathHelper.mkdirs(p);
                r.getAuditor().audit(
                        AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("add-storage").build());
                r.modifyState(s -> s.storageLocations.add(p));
            } else if (config.remove() != null) {
                r.getAuditor().audit(
                        AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("remove-storage").build());
                Path p = Paths.get(config.remove());
                r.modifyState(s -> s.storageLocations.remove(p));
            } else if (config.list()) {
                r.getStorageLocations().forEach(out()::println);
            } else {
                out().println("Nothing to do.");
            }
        }
    }

}
