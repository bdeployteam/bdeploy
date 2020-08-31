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
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.UnitHelper;
import io.bdeploy.jersey.audit.AuditRecord;
import io.bdeploy.jersey.fs.FileSystemSpaceService;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.StorageTool.StorageConfig;

/**
 * Manages storage locations.
 */
@Help("Manage storage locations for the minion.")
@ToolCategory(MinionServerCli.MGMT_TOOLS)
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
    protected RenderableResult run(StorageConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");

        try (MinionRoot r = new MinionRoot(Paths.get(config.root()), getActivityReporter())) {
            List<Path> original = r.getStorageLocations();
            if (config.add() != null) {
                Path p = Paths.get(config.add());
                if (original.contains(p)) {
                    return createResultWithMessage(p + " already registered.");
                }
                PathHelper.mkdirs(p);
                r.getAuditor().audit(
                        AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("add-storage").build());
                r.modifyState(s -> s.storageLocations.add(p));
                return createSuccess();
            } else if (config.remove() != null) {
                r.getAuditor().audit(
                        AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("remove-storage").build());
                Path p = Paths.get(config.remove());
                r.modifyState(s -> s.storageLocations.remove(p));
                return createSuccess();
            } else if (config.list()) {
                DataTable t = createDataTable();
                t.column("Storage Path", 100).column("Free Space", 20);
                FileSystemSpaceService fsss = new FileSystemSpaceService();
                r.getStorageLocations()
                        .forEach(l -> t.row().cell(l).cell(UnitHelper.formatFileSize(fsss.getFreeSpace(l))).build());
                return t;
            } else {
                return createNoOp();
            }
        }
    }

}
