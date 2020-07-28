package io.bdeploy.interfaces.manifest.history.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MinionRuntimeHistory {

    private final Map<String, MinionApplicationRuntimeHistory> applications = new HashMap<>();

    public MinionRuntimeHistory record(MinionRuntimeHistoryRecord record, String applicationId) {
        if (applications.containsKey(applicationId)) {
            applications.get(applicationId).addRecord(record);
        } else {
            applications.put(applicationId, new MinionApplicationRuntimeHistory(record));
        }
        return this;
    }

    public Map<String, MinionApplicationRuntimeHistory> getHistory() {
        return applications;
    }

    public boolean isEmpty() {
        return applications.isEmpty();
    }

    public Set<Map.Entry<String, MinionApplicationRuntimeHistory>> entrySet() {
        return applications.entrySet();
    }
}
