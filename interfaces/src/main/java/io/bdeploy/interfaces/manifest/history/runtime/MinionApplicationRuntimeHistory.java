package io.bdeploy.interfaces.manifest.history.runtime;

import java.util.ArrayList;
import java.util.List;

public class MinionApplicationRuntimeHistory {

    private final List<MinionRuntimeHistoryRecord> records;

    MinionApplicationRuntimeHistory() {
        records = new ArrayList<>();
    }

    MinionApplicationRuntimeHistory(MinionRuntimeHistoryRecord record) {
        records = new ArrayList<>();
        records.add(record);
    }

    public List<MinionRuntimeHistoryRecord> getRecords() {
        return records;
    }

    public void addRecord(MinionRuntimeHistoryRecord record) {
        records.add(record);
    }
}
