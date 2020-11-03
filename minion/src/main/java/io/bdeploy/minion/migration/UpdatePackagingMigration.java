package io.bdeploy.minion.migration;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map.Entry;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.Key;
import io.bdeploy.bhive.op.ExportTreeOperation;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.TreeLoadOperation;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.minion.MinionRoot;

/**
 * Check for a BDeploy minion manifest which contains the launcher update ZIP packages.
 * <p>
 * This happens when updating from pre-1.2.0 to 1.2.0 as the packaging changed to include the launchers.
 * The old minion will include the launchers in the manifest, the new minion will not. If a minion manifest
 * contains the launcher ZIP packages, extract and import them to make the launchers available.
 */
public class UpdatePackagingMigration {

    private UpdatePackagingMigration() {
    }

    private static final Logger log = LoggerFactory.getLogger(UpdatePackagingMigration.class);

    public static void run(MinionRoot root) throws IOException {
        if (!root.isMaster()) {
            // the minion manifest will include the launcher also on the node, as the update is pushed to them
            // but we don't need the launchers there.
            return;
        }

        // check the minion root hive for the manifest(s).
        BHive hive = root.getHive();

        // tag to check is the currently running version. we only need to check the exact version running.
        // if we "miss" the migration because of another update, the updated code will already have put the
        // launchers in the correct place anyway.
        Manifest.Key currentKey = UpdateHelper.getCurrentKey();

        if (!Boolean.TRUE.equals(hive.execute(new ManifestExistsOperation().setManifest(currentKey)))) {
            // not worth /really/ failing. this should never happen, worst case: manual upload of launchers required.
            log.warn("Cannot find manifest for currently running minion version ({}), cannot perform migration!", currentKey);
            return;
        }

        Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(currentKey));
        Tree pkgTree = hive.execute(new TreeLoadOperation().setTree(mf.getRoot()));

        // don't use .getNamedEntry as it will throw if the entry does not exist.
        Optional<Entry<Key, ObjectId>> launcherEntry = pkgTree.getChildren().entrySet().stream()
                .filter(e -> e.getKey().getName().equals(UpdateHelper.SW_LAUNCHER)).findFirst();

        if (!launcherEntry.isPresent() || launcherEntry.get().getKey().getType() != Tree.EntryType.TREE) {
            // not present means we're save, no migration required.
            return;
        }

        // we do have the launcher tree entry, which means we might find some launcher ZIP files in there.
        // export the tree to a temporary directory and import the ZIP files.
        Path tmpDir = Files.createTempDirectory("mig-");
        try {
            Path zipDir = tmpDir.resolve(UpdateHelper.SW_LAUNCHER);
            hive.execute(new ExportTreeOperation().setSourceTree(launcherEntry.get().getValue()).setTargetPath(zipDir));

            // for each zip file in the exported tree, import a launcher :)
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(zipDir, "launcher-*.zip")) {
                for (Path zip : stream) {
                    try {
                        for (Manifest.Key k : UpdateHelper.importUpdate(zip, tmpDir, hive)) {
                            log.info("Imported during migration: {}", k);
                        }
                    } catch (Exception e) {
                        // happens if the hive already contains the manifest, e.g. if imported manually (or via CLI) beforehand.
                        log.warn("Cannot migrate nested update package {}", zip, e);
                    }
                }
            }
        } finally {
            PathHelper.deleteRecursive(tmpDir);
        }
    }

}
