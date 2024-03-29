package io.bdeploy.interfaces.manifest.history.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * The runtime history of all applications running in a given version.
 */
public class MinionRuntimeHistory {

    private final Map<String, MinionApplicationRuntimeHistory> applications = new HashMap<>();

    public MinionRuntimeHistory recordEvent(MinionRuntimeHistoryRecord event, String applicationId) {
        MinionApplicationRuntimeHistory history = applications.computeIfAbsent(applicationId,
                k -> new MinionApplicationRuntimeHistory());
        history.addRecord(event);
        return this;
    }

    public Map<String, MinionApplicationRuntimeHistory> getHistory() {
        return applications;
    }

    public boolean isEmpty() {
        return applications.isEmpty();
    }

}
