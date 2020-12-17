package io.bdeploy.minion.migration;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.minion.MinionHelper;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.MinionState;
import io.bdeploy.ui.api.Minion;

/**
 * Creates {@linkplain MinionManifest manifest} entries for all minions listed in an old minion state map.
 */
public class MinionStateMigration {

    private static final Logger log = LoggerFactory.getLogger(MinionStateMigration.class);

    private MinionStateMigration() {
    }

    public static void run(MinionRoot root) {
        // Check if we already have a minion manifest
        // In that case the migration is not required
        MinionManifest manifest = new MinionManifest(root.getHive());
        MinionConfiguration minionConfiguration = manifest.read();
        if (minionConfiguration != null) {
            return;
        }
        log.info("Migrating minion state entries to manifest entries.");

        // Find the state.json and read it with the old state class.
        // This needs to happen before the first modification of the state
        // as otherwise the file is written without the field.
        OldMinionState migState = root.getPartialStateForMigration(OldMinionState.class);

        // Ensure that the self name is set
        MinionState state = root.getState();
        if (state.self == null) {
            root.modifyState(s -> s.self = Minion.DEFAULT_NAME);
            state = root.getState();
            log.info("Setting self name to '{}'", Minion.DEFAULT_NAME);
        }

        // Create an entry for each node
        minionConfiguration = new MinionConfiguration();
        for (Map.Entry<String, RemoteService> entry : migState.minions.entrySet()) {
            String name = entry.getKey();
            RemoteService remote = entry.getValue();

            // Minions contains an entry for ourself
            if (state.self.equals(name)) {
                MinionDto dto = MinionDto.create(root.isMaster(), remote);
                minionConfiguration.addMinion(name, dto);
            } else {
                log.info("Try to contact '{}' using {}", name, remote.getUri());
                MinionStatusDto status = MinionHelper.tryContactMinion(remote, 3, 15);
                if (status == null) {
                    throw new IllegalStateException("Migration failed because not all minions are reachable. " //
                            + "Ensure that they are running and try again.");
                }
                MinionDto config = status.config;
                log.info("Minion successfully contacted. Version={} OS={}", config.version, config.os);
                minionConfiguration.addMinion(name, config);
            }
            log.info("Created manifest entry for '{}'", name);
        }

        // Persist the manifest
        manifest.update(minionConfiguration);

        log.info("Migration successfully done.");
    }

    private static class OldMinionState {

        /**
         * Known other minions. Currently only used on the master minion
         */
        public SortedMap<String, RemoteService> minions = new TreeMap<>();
    }

}
