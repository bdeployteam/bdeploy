package io.bdeploy.interfaces.manifest.history;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;

/**
 * Encapsulates book-keeping on an {@link InstanceManifest}. This history keeps track of all actions performed on a certain
 * {@link InstanceManifest}.
 * <p>
 * History is kept per instance tag, this it is only available when queried with the matching instance tag.
 */
public class InstanceManifestHistory {

    public enum Action {
        CREATE,
        INSTALL,
        UNINSTALL,
        ACTIVATE,
        DEACTIVATE
    }

    private final MetaManifest<History> meta;
    private final BHiveExecution hive;

    public InstanceManifestHistory(Manifest.Key instanceManifest, BHiveExecution hive) {
        this.hive = hive;
        this.meta = new MetaManifest<>(instanceManifest, true, History.class);
    }

    /**
     * Place a record of the given action in the history of the underlying {@link InstanceManifest}.
     *
     * @param action the performed action.
     */
    public void record(Action action) {
        store(readOrCreate().append(new Record(action, System.currentTimeMillis())));
    }

    /**
     * Finds the timestamp of the most recent record of the given {@link Action}.
     *
     * @param action the action to look up.
     * @return the timestamp or 0 (zero) if no record has been found.
     */
    public long findMostRecent(Action action) {
        return readOrCreate().records.stream().filter(a -> a.action == action)
                .sorted((a, b) -> Long.compare(b.timestamp, a.timestamp)).map(a -> a.timestamp).findFirst().orElse(0L);
    }

    /**
     * Finds the first occurrence of the given {@link Action}.
     * 
     * @param action the action to look up.
     * @return the timestamp or 0 (zero) if no record has been found.
     */
    public long findFirst(Action action) {
        return readOrCreate().records.stream().filter(a -> a.action == action).findFirst().map(a -> a.timestamp).orElse(0L);
    }

    private History readOrCreate() {
        History stored = meta.read(hive);
        if (stored == null) {
            return new History();
        }
        return stored;
    }

    private void store(History history) {
        meta.write(hive, history);
    }

    private static final class History {

        public List<Record> records = new ArrayList<>();

        public History append(Record record) {
            this.records.add(record);
            return this;
        }
    }

    private static final class Record {

        public Action action;
        public long timestamp;

        @JsonCreator
        public Record(@JsonProperty("action") Action action, @JsonProperty("timestamp") long timestamp) {
            this.action = action;
            this.timestamp = timestamp;
        }

    }

}
