package io.bdeploy.interfaces.manifest.state;

import java.util.function.Supplier;

import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;

/**
 * Stores and retrieves instance state.
 */
public class InstanceState {

    private final BHiveExecution hive;
    private final MetaManifest<InstanceStateRecord> meta;
    private Supplier<InstanceStateRecord> initSupplier;

    public InstanceState(Manifest.Key instanceManifest, BHiveExecution hive) {
        this.hive = hive;
        this.meta = new MetaManifest<>(instanceManifest, false, InstanceStateRecord.class);
    }

    /**
     * @param initSupplier a {@link Supplier} capable of migrating from the old information scheme to the new one.
     * @deprecated only used for migration from old (online) scheme to new (offline) scheme.
     */
    @Deprecated
    public InstanceState setMigrationProvider(Supplier<InstanceStateRecord> initSupplier) {
        this.initSupplier = initSupplier;
        return this;
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
            // TODO: remove in 2.0.0. just return empty instance always.
            if (this.initSupplier == null) {
                return new InstanceStateRecord();
            } else {
                InstanceStateRecord supplied = initSupplier.get();
                store(supplied);
                return supplied;
            }
        }
        return stored;
    }

    private void store(InstanceStateRecord record) {
        meta.write(hive, record);
    }

}
