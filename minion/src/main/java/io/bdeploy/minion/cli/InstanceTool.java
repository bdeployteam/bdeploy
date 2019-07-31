package io.bdeploy.minion.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.InstanceImportExportHelper;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.minion.cli.InstanceTool.InstanceConfig;

@Help("Import and Export existing instance configurations")
@CliName("instance")
public class InstanceTool extends ConfiguredCliTool<InstanceConfig> {

    public @interface InstanceConfig {

        @Help("Path to the local hive used for loading application descriptors and storing the deployment manifests when loading")
        @EnvironmentFallback("BHIVE")
        String hive();

        @Help("Path to a ZIP file containing an export produced with this command")
        String importFrom();

        @Help("Path to a non-existing ZIP file where to export a given instance configuration")
        String exportTo();

        @Help(value = "UUID of the instance. When exporting must exist. When importing may exist (a new version is created). If not given, a random new UUID is generated.")
        String uuid();

        @Help(value = "When exporting, optional tag of the existing instance. Otherwise the latest tag is exported.")
        String tag();
    }

    public InstanceTool() {
        super(InstanceConfig.class);
    }

    @Override
    protected void run(InstanceConfig config) {
        helpAndFailIfMissing(config.hive(), "--hive missing");

        try (BHive hive = new BHive(Paths.get(config.hive()).toUri(), getActivityReporter())) {
            if (config.exportTo() != null) {
                doExport(Paths.get(config.exportTo()), hive, config.uuid(), config.tag());
            } else if (config.importFrom() != null) {
                doImport(Paths.get(config.importFrom()), hive, config.uuid());
            } else {
                helpAndFail("Nothing to do...");
            }
        }
    }

    /**
     * @param zip the ZIP file to read from
     * @param hive the {@link BHive} to import to
     * @param uuid the target UUID of the instance.
     */
    private void doImport(Path zip, BHive hive, String uuid) {
        if (uuid == null) {
            uuid = UuidHelper.randomId();
        }
        Manifest.Key created = InstanceImportExportHelper.importFrom(zip, hive, uuid);
        InstanceManifest imf = InstanceManifest.of(hive, created);
        out().println("Created '" + imf.getConfiguration().uuid + "': " + imf.getConfiguration().name);
    }

    /**
     * @param zip the ZIP file to write to
     * @param hive the {@link BHive} to read from
     * @param uuid the UUID of the instance to export
     * @param tag the optional tag to export. Otherwise the newest is exported.
     */
    private void doExport(Path zip, BHive hive, String uuid, String tag) {
        if (Files.exists(zip)) {
            helpAndFail("ZIP file already exists: " + zip);
        }

        String rootName = InstanceManifest.getRootName(uuid);
        if (tag == null) {
            Optional<Long> found = hive.execute(new ManifestMaxIdOperation().setManifestName(rootName));
            if (!found.isPresent()) {
                throw new IllegalStateException("Cannot determin version for instance: " + uuid);
            }
            tag = String.valueOf(found.get());
        }
        Manifest.Key key = new Manifest.Key(rootName, tag);
        InstanceManifest imf = InstanceManifest.of(hive, key);
        if (imf == null) {
            throw new IllegalStateException("Cannot export, instance not found: " + key);
        }

        InstanceImportExportHelper.exportTo(zip, hive, imf);
    }

}
