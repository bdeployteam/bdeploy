package io.bdeploy.interfaces.manifest.history.runtime;

import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;

public class MinionRuntimeHistoryManager {

    private final MetaManifest<MinionRuntimeHistory> meta;
    private final BHiveExecution hive;

    public MinionRuntimeHistoryManager(Manifest.Key manifest, BHiveExecution hive) {
        this.meta = new MetaManifest<>(manifest, true, MinionRuntimeHistory.class);
        this.hive = hive;
    }

    public MinionRuntimeHistory getFullHistory() {
        return readOrCreate();
    }

    /**
     * Records a single state change event.
     * <p>
     * The method is synchronized, since the same instance of the {@link MinionRuntimeHistoryManager} is used for process
     * controllers. In case a process crashes and is immediately restarted (for instance) events may occur so fast on different
     * threads for the same application, that a key conflict can occur during recording.
     * </p>
     */
    public synchronized void record(long processId, int exitCode, ProcessState action, String applicationId, String user) {
        store(readOrCreate().record(new MinionRuntimeHistoryRecord(processId, exitCode, action, user, System.currentTimeMillis()),
                applicationId));
    }

    private MinionRuntimeHistory readOrCreate() {
        MinionRuntimeHistory stored = meta.read(hive);
        if (stored == null) {
            return new MinionRuntimeHistory();
        }
        return stored;
    }

    private void store(MinionRuntimeHistory history) {
        meta.write(hive, history);
    }
}
