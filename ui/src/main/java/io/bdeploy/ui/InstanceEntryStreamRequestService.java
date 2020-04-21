package io.bdeploy.ui;

import java.util.Map;
import java.util.TreeMap;

import org.jvnet.hk2.annotations.Service;

import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.directory.InstanceDirectoryEntry;

@Service
public class InstanceEntryStreamRequestService {

    private final Map<String, EntryRequest> tokens = new TreeMap<>();

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

    public String createRequest(EntryRequest rq) {
        String token;

        do {
            token = UuidHelper.randomId();
        } while (tokens.putIfAbsent(token, rq) != null);

        return token;
    }

    public EntryRequest consumeRequestToken(String token) {
        EntryRequest rq = tokens.get(token);
        if (rq == null) {
            throw new IllegalArgumentException("Illegal access token");
        }
        return rq;
    }

}
