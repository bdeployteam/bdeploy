package io.bdeploy.minion.migration;

import java.util.Map;

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

/**
 * Creates {@linkplain MinionManifest manifest} entries for all minions listed in the {@linkplain MinionState#minions} map.
 */
public class MinionStateMigration {

    private static final Logger log = LoggerFactory.getLogger(MinionStateMigration.class);

    private MinionStateMigration() {
    }

    @SuppressWarnings("removal")
    public static void run(MinionRoot root) {
        // Check if we already have a minion manifest
        // In that case the migration is not required
        MinionManifest manifest = new MinionManifest(root.getHive());
        MinionConfiguration minionConfiguration = manifest.read();
        if (minionConfiguration != null) {
            return;
        }
        log.info("Migrating minion state entries to manifest entries.");

        // Ensure that the self name is set
        MinionState state = root.getState();
        if (state.self == null) {
            root.modifyState(s -> s.self = MinionRoot.DEFAULT_NAME);
            state = root.getState();
            log.info("Setting self name to '{}'", MinionRoot.DEFAULT_NAME);
        }

        // Create an entry for each slave
        minionConfiguration = new MinionConfiguration();
        for (Map.Entry<String, RemoteService> entry : state.minions.entrySet()) {
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

        // Clear out the minions property
        root.modifyState(s -> s.minions.clear());
        log.info("Migration successfully done.");
    }

}
