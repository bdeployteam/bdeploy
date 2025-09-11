package io.bdeploy.interfaces;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ExportTreeOperation;
import io.bdeploy.bhive.op.ImportTreeOperation;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory.Action;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.nodes.NodeType;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Helps with importing and exporting instance configurations
 * <p>
 * Creates and reads "bundles" which contain the complete configuration for the instance and all nodes, along with all
 * configuration files.
 */
public class InstanceImportExportHelper {

    private static final Logger log = LoggerFactory.getLogger(InstanceImportExportHelper.class);

    private static final String CONFIG_DIR = "config";
    private static final String INSTANCE_JSON = "instance.json";

    private InstanceImportExportHelper() {
    }

    /**
     * @param zipFilePath the {@link Path} to the ZIP file to create. The file may NOT exist yet.
     * @param source the source {@link BHive} to read data from.
     * @param imf the {@link InstanceManifest} to export.
     */
    public static void exportTo(Path zipFilePath, BHive source, InstanceManifest imf) {
        if (Files.exists(zipFilePath)) {
            throw new IllegalArgumentException("ZIP may not yet exist: " + zipFilePath);
        }

        InstanceCompleteConfigDto export = new InstanceCompleteConfigDto();

        export.config = imf.getConfiguration();
        export.minions.putAll(imf.getInstanceNodeConfigurations(source));

        try (FileSystem zfs = PathHelper.openZip(zipFilePath)) {
            Path zroot = zfs.getPath("/");

            Files.write(zroot.resolve(INSTANCE_JSON), StorageHelper.toRawBytes(export));

            if (imf.getConfiguration().configTree != null) {
                source.execute(new ExportTreeOperation().setSourceTree(imf.getConfiguration().configTree)
                        .setTargetPath(zroot.resolve(CONFIG_DIR)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create ZIP: " + zipFilePath, e);
        }
    }

    /**
     * Imports a single instance from the given ZIP file into the given target hive. All existing IDs contained in the provided
     * ZIP will be replaced by this ID. The target {@link BHive} may already contain an {@link InstanceManifest} with the given
     * ID, in which case a new version will be created. If the ID is not yet used, a new {@link InstanceManifest} will be
     * created instead.
     *
     * @param zipFilePath
     *            {@link Path} to the ZIP file to read from.
     * @param target
     *            target {@link BHive} to import to.
     * @param id
     *            target ID for the instance.
     * @param minions
     *            available minions and their OS
     * @param context the {@link SecurityContext}
     * @return the resulting {@link Key} in the target {@link BHive}
     */
    public static Manifest.Key importFrom(Path zipFilePath, BHive target, String id, Map<String, MinionDto> minions,
            SecurityContext context) {
        try (FileSystem zfs = PathHelper.openZip(zipFilePath)) {
            Path zroot = zfs.getPath("/");

            InstanceCompleteConfigDto cfg = StorageHelper.fromRawBytes(Files.readAllBytes(zroot.resolve(INSTANCE_JSON)),
                    InstanceCompleteConfigDto.class);

            try (Transaction t = target.getTransactions().begin()) {
                ObjectId cfgId = null;
                Path cfgDir = zroot.resolve(CONFIG_DIR);
                if (Files.exists(cfgDir)) {
                    cfgId = target.execute(new ImportTreeOperation().setSkipEmpty(true).setSourcePath(cfgDir));
                }

                return importFromData(target, cfg, cfgId, id, minions, context);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read ZIP: " + zipFilePath, e);
        }
    }

    private static Manifest.Key importFromData(BHive target, InstanceCompleteConfigDto dto, ObjectId cfgId, String id,
            Map<String, MinionDto> minions, SecurityContext context) {
        if (!Objects.equals(dto.config.configTree, cfgId)) {
            log.warn("Configuration tree has unexpected ID: {} <-> {}", dto.config.configTree, cfgId);
        }
        validateId(id);

        InstanceConfiguration icfg = dto.config;
        icfg.configTree = cfgId;
        icfg.id = id;

        // system is explicitly cleared out - will have to be associated later on manually.
        icfg.system = null;

        // if there is an existing instance, re-use the configured target server, name & description, etc. to avoid confusion!
        String rootName = InstanceManifest.getRootName(id);
        Optional<Long> latest = target.execute(new ManifestMaxIdOperation().setManifestName(rootName));
        Set<String> idPool = new TreeSet<>();
        if (latest.isPresent()) {
            InstanceManifest existing = InstanceManifest.of(target, new Manifest.Key(rootName, String.valueOf(latest.get())));
            alignInstanceInformation(icfg, existing);

            // gather all IDs for every artifact which has an ID except for the instance itself (currently only applications).
            // all IDs which are NOT yet known to the instance MUST be re-assigned to avoid clashes when "copying" an instance.
            // in case there is no existing (latest) version yet, the pool stays empty and all IDs are re-assigned.
            for (var cfg : existing.getInstanceNodeConfigurations(target).values()) {
                for (ApplicationConfiguration app : cfg.applications) {
                    validateId(app.id);
                    idPool.add(app.id);
                }
            }
        }

        InstanceManifest.Builder imfb = new InstanceManifest.Builder().setInstanceConfiguration(icfg);
        for (Map.Entry<String, InstanceNodeConfiguration> node : dto.minions.entrySet()) {
            InstanceNodeManifest.Builder inmBuilder = new InstanceNodeManifest.Builder();
            InstanceNodeConfiguration nodeCfg = node.getValue();

            // align redundant copies of certain flags
            nodeCfg.copyRedundantFields(icfg);
            nodeCfg.mergeVariables(icfg, null, null); // no system, don't resolve config files - client nodes don't have config on import anyway.
            reAssignAppIds(idPool, nodeCfg);

            String minionName = node.getKey();
            if (nodeCfg.nodeType != NodeType.CLIENT) {
                MinionDto minionDto = minions.get(minionName);
                reAssignApplications(target, nodeCfg, minionName, minionDto);
                inmBuilder.addConfigTreeId(InstanceNodeManifest.ROOT_CONFIG_NAME, cfgId);
            }

            inmBuilder.setInstanceNodeConfiguration(nodeCfg);
            inmBuilder.setMinionName(minionName);

            // if there is already a version, we need to align the inm version to the target version
            if (latest.isPresent()) {
                inmBuilder.setKey(new Manifest.Key(nodeCfg.id + "/" + minionName, Long.toString(latest.get() + 1)));
            }

            imfb.addInstanceNodeManifest(minionName, inmBuilder.insert(target));
        }

        Key result = imfb.insert(target);
        InstanceManifest.of(target, result).getHistory(target).recordAction(Action.CREATE,
                context != null ? context.getUserPrincipal().getName() : ApiAccessToken.SYSTEM_USER, null);
        return result;
    }

    private static void reAssignApplications(BHive hive, InstanceNodeConfiguration nodeCfg, String minionName, MinionDto minion) {
        if (nodeCfg.nodeType == NodeType.CLIENT) {
            return;
        }
        for (ApplicationConfiguration app : nodeCfg.applications) {
            Manifest.Key appKey = app.application;
            ScopedManifestKey smk = ScopedManifestKey.parse(appKey);

            if (minion == null || smk == null || smk.getOperatingSystem() == minion.os) {
                continue; // minion not found, not OS dependent, or OS already fine.
            }

            ScopedManifestKey newKey = new ScopedManifestKey(smk.getName(), minion.os, smk.getTag());
            Boolean exists = hive.execute(new ManifestExistsOperation().setManifest(newKey.getKey()));
            if (Boolean.TRUE.equals(exists)) {
                log.info("Updating application OS, setting {} to {}.", app.name, newKey);
                app.application = newKey.getKey();
            } else {
                log.warn("Failed to update application OS, cannot find {} for {}.", newKey, app.name);
            }
        }
    }

    private static void reAssignAppIds(Set<String> idPool, InstanceNodeConfiguration nodeCfg) {
        for (ApplicationConfiguration app : nodeCfg.applications) {
            if (idPool.contains(app.id)) {
                // all is well, this is an update for an existing application
                continue;
            }

            var oldUid = app.id;

            // need to re-assign ID, as this might be a copy of an application on the same server.
            // this would create various issues when installing (clash of IDs), especially on the client(s).
            app.id = UuidHelper.randomId();

            // need to swap the application UID in the control group's process order as well.
            var controlGroup = nodeCfg.controlGroups.stream().filter(g -> g.processOrder.contains(oldUid)).findAny();
            if (controlGroup.isPresent()) {
                var order = controlGroup.get().processOrder;
                var index = order.indexOf(oldUid);

                order.set(index, app.id);
            }
        }
    }

    private static void alignInstanceInformation(InstanceConfiguration icfg, InstanceManifest existing) {
        icfg.name = existing.getConfiguration().name;
        icfg.description = existing.getConfiguration().description;
        icfg.purpose = existing.getConfiguration().purpose;

        // disallow product switch!
        if (!icfg.product.getName().equals(existing.getConfiguration().product.getName())) {
            throw new IllegalStateException(
                    "Product switch not allowed: old=" + existing.getConfiguration().product + ", new=" + icfg.product);
        }
    }

    /**
     * Validates whether the given ID is valid - in this case this means it may not try
     * to sneak in characters which could cause issues when using as path-component later.
     */
    private static void validateId(String id) {
        if (id == null || id.contains("..") || id.contains("/") || id.contains("\\")) {
            throw new IllegalStateException("Invalid ID found: " + id);
        }
    }

    /**
     * {@link InstanceManifest} and {@link InstanceNodeManifest} hold the {@link InstanceConfiguration} and
     * {@link InstanceNodeConfiguration} separately. This helper class bundles them together for hierarchical storage in an
     * export.
     */
    private static final class InstanceCompleteConfigDto {

        public InstanceConfiguration config = new InstanceConfiguration();

        public final SortedMap<String, InstanceNodeConfiguration> minions = new TreeMap<>();

    }

}
