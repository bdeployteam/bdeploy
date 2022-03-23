package io.bdeploy.interfaces.manifest.state;

import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;

/**
 * Stores and retrieves instance state.
 */
public class InstanceState {

    private final BHiveExecution hive;
    private final MetaManifest<InstanceStateRecord> meta;

    public InstanceState(Manifest.Key instanceManifest, BHiveExecution hive) {
        this.hive = hive;
        this.meta = new MetaManifest<>(instanceManifest, false, InstanceStateRecord.class);
    }

    /**
     * @param tag the tag to mark as active instance. Only one instance is active at a time.
     */
    public void activate(String tag) {
        store(readOrCreate().setActive(tag));
    }

    /**
     * @param tag the tag to mark as installed.
     */
    public void install(String tag) {
        store(readOrCreate().setInstalled(tag));
    }

    /**
     * @param tag the tag to remove from the list of installed versions.
     */
    public void uninstall(String tag) {
        store(readOrCreate().setUninstalled(tag));
    }

    /**
     * @return the currently persisted state of the instance version.
     */
    public InstanceStateRecord read() {
        return readOrCreate();
    }

    private InstanceStateRecord readOrCreate() {
        InstanceStateRecord stored = meta.read(hive);
        if (stored == null) {
            return new InstanceStateRecord();
        }
        return stored;
    }

    private void store(InstanceStateRecord rec) {
        meta.write(hive, rec);
    }

}
