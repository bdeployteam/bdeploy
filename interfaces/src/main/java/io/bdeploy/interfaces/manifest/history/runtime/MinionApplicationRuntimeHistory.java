package io.bdeploy.interfaces.manifest.history.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * The runtime history of an application.
 */
public class MinionApplicationRuntimeHistory {

    private final List<MinionRuntimeHistoryRecord> records = new ArrayList<>();

    public List<MinionRuntimeHistoryRecord> getRecords() {
        return records;
    }

    public void addRecord(MinionRuntimeHistoryRecord record) {
        records.add(record);
    }
}
