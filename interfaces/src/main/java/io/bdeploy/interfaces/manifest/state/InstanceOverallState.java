
package io.bdeploy.interfaces.manifest.state;

import java.util.List;

import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.manifest.state.InstanceOverallStateRecord.OverallStatus;

public class InstanceOverallState {

    private final BHiveExecution hive;
    private final MetaManifest<InstanceOverallStateRecord> meta;

    public InstanceOverallState(Manifest.Key instanceManifest, BHiveExecution hive) {
        this.hive = hive;
        this.meta = new MetaManifest<>(instanceManifest, false, InstanceOverallStateRecord.class);
    }

    public void update(OverallStatus status, List<String> messages) {
        InstanceOverallStateRecord rec = new InstanceOverallStateRecord();
        rec.status = status;
        rec.timestamp = System.currentTimeMillis();
        rec.messages = messages;
        store(rec);
    }

    /**
     * @return the currently persisted state of the instance version.
     */
    public InstanceOverallStateRecord read() {
        return readOrCreate();
    }

    private InstanceOverallStateRecord readOrCreate() {
        InstanceOverallStateRecord stored = meta.read(hive);
        if (stored == null) {
            return new InstanceOverallStateRecord();
        }
        return stored;
    }

    private void store(InstanceOverallStateRecord rec) {
        meta.write(hive, rec);
    }

}
