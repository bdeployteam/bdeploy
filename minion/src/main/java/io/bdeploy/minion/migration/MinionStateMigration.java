package io.bdeploy.minion.migration;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.MinionState;

/**
 * Creates {@linkplain MinionManifest manifest} entries for all minions listed in the {@linkplain MinionState#minions} map.
 */
public class MinionStateMigration {

    private static final Logger log = LoggerFactory.getLogger(MinionStateMigration.class);

    @SuppressWarnings("removal")
    public static void run(MinionRoot root) {
        // Check if we already have a minion manifest
        // In that case the migration is not required
        MinionManifest manifest = new MinionManifest(root.getHive());
        MinionConfiguration minionConfiguration = manifest.read();
        if (minionConfiguration != null) {
            return;
        }
        MinionState state = root.getState();

        // Create an entry for each slave
        log.info("Migrating minion state entries to manifest entries.");
        minionConfiguration = new MinionConfiguration();
        for (Map.Entry<String, RemoteService> entry : state.minions.entrySet()) {
            String name = entry.getKey();
            RemoteService remote = entry.getValue();

            // Minions contains an entry for ourself
            if (state.self.equals(name)) {
                MinionDto dto = MinionDto.create(remote);
                dto.master = root.isMaster();
                minionConfiguration.addMinion(name, dto);
            } else {
                MinionDto dto = doMigrate(name, remote);
                minionConfiguration.addMinion(name, dto);
            }
        }

        // Persist the manifest
        manifest.update(minionConfiguration);

        // Clear out the minions property
        root.modifyState(s -> {
            s.minions.clear();
        });
        log.info("Migration successfully done.");
    }

    private static MinionDto doMigrate(String name, RemoteService remote) {
        // Try to contact the remote slave
        int waitTime = 15;
        int retryCount = 3;
        for (int i = 0; i < retryCount; i++) {
            try {
                log.info("Contacting '{}' using {}", name, remote.getUri());
                MinionStatusResource status = ResourceProvider.getResource(remote, MinionStatusResource.class, null);
                MinionDto config = status.getStatus().config;
                log.info("Minion successfully contacted. Version={} OS={}", config.version, config.os);
                return config;
            } catch (Exception ex) {
                try {
                    log.info("Failed to contact minion: {}", ex.getMessage());
                    if (i < retryCount - 1) {
                        log.info("Waiting {} seconds until next attempt ({}/{})", waitTime, i + 1, retryCount);
                        TimeUnit.SECONDS.sleep(waitTime);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for next retry.");
                }
            }
        }
        throw new RuntimeException("Migration failed because not all minions are reachable. " //
                + "Ensure that they are running and try again.");
    }
}
