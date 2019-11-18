package io.bdeploy.minion;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.interfaces.remote.ResourceProvider;

/**
 * Provides static methods to communicate with a minion.
 */
public class MinionHelper {

    private static final Logger log = LoggerFactory.getLogger(MinionHelper.class);

    private MinionHelper() {
    }

    /**
     * Tries to contact the minion with the given name and returns the status information.
     *
     * @param remote
     *            remote service to contact
     * @param retryCount
     *            number of times to retry contacting. 1 = no retry attempts.
     * @param waitTimeInSec
     *            time in seconds to wait between attempts
     * @return the status DTO or {@code null} if the minion is offline
     */
    public static MinionStatusDto tryContactMinion(RemoteService remote, int retryCount, int waitTimeInSec) {
        for (int i = 0; i < retryCount; i++) {
            try {
                MinionStatusResource service = ResourceProvider.getResource(remote, MinionStatusResource.class, null);
                return service.getStatus();
            } catch (Exception ex) {
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Failed to contact minion: {}", ex.getMessage());
                    }
                    if (i < retryCount - 1) {
                        TimeUnit.SECONDS.sleep(waitTimeInSec);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for next retry.");
                }
            }
        }
        return null;
    }

}
