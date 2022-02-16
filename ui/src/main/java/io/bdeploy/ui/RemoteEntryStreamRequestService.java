package io.bdeploy.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jvnet.hk2.annotations.Service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;

@Service
public class RemoteEntryStreamRequestService {

    private final Cache<String, EntryRequest> tokens = CacheBuilder.newBuilder().expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(1_000).build();

    public static final class EntryRequest {

        public String minion;
        public String instanceId;
        public List<RemoteDirectoryEntry> entries = new ArrayList<>();

        public EntryRequest(String minion, String instanceId, RemoteDirectoryEntry entry) {
            this.minion = minion;
            this.instanceId = instanceId;
            this.entries.add(entry);
        }

        public EntryRequest(String minion, String instanceId, List<RemoteDirectoryEntry> entries) {
            this.minion = minion;
            this.instanceId = instanceId;
            this.entries.addAll(entries);
        }

        public RemoteDirectoryEntry getEntry() {
            if (entries.size() != 1) {
                throw new IllegalStateException("Not one entry");
            }
            return entries.get(0);
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
