package io.bdeploy.interfaces.manifest.history.runtime;

import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;

public class MinionRuntimeHistoryManager {

    private final MetaManifest<MinionRuntimeHistory> meta;
    private final BHiveExecution hive;
    private final Manifest.Key parent;

    public MinionRuntimeHistoryManager(Manifest.Key Manifest, BHiveExecution hive) {
        this.meta = new MetaManifest<>(Manifest, true, MinionRuntimeHistory.class);
        this.hive = hive;
        parent = Manifest;

    }

    public MinionRuntimeHistory getFullHistory() {
        return readOrCreate();
    }

    public void record(ProcessState action, String applicationId) {
        store(readOrCreate().record(new MinionRuntimeHistoryRecord(action, System.currentTimeMillis()), applicationId));
    }

    private MinionRuntimeHistory readOrCreate() {

        MinionRuntimeHistory stored = meta.read(hive);
        if (stored == null) {
            return new MinionRuntimeHistory();
        }
        return stored;
    }

    public void store(MinionRuntimeHistory history) {
        meta.write(hive, history);
    }
}
