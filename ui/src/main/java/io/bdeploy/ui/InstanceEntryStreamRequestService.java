package io.bdeploy.ui;

import java.util.concurrent.TimeUnit;

import org.jvnet.hk2.annotations.Service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.directory.InstanceDirectoryEntry;

@Service
public class InstanceEntryStreamRequestService {

    private final Cache<String, EntryRequest> tokens = CacheBuilder.newBuilder().expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(1_000).build();

    public static final class EntryRequest {

        public String minion;
        public String instanceId;
        public InstanceDirectoryEntry entry;

        public EntryRequest(String minion, String instanceId, InstanceDirectoryEntry entry) {
            this.minion = minion;
            this.instanceId = instanceId;
            this.entry = entry;
        }
    }

    public synchronized String createRequest(EntryRequest rq) {
        String token;

        do {
            token = UuidHelper.randomId();
        } while (tokens.getIfPresent(token) != null);

        // manually add to the cache.
        tokens.put(token, rq);

        return token;
    }

    public synchronized EntryRequest consumeRequestToken(String token) {
        EntryRequest rq = tokens.getIfPresent(token);
        if (rq == null) {
            throw new IllegalArgumentException("Illegal access token");
        }
        return rq;
    }

}
