package io.bdeploy.interfaces.manifest.properties;

import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;

public class CustomProperties {

    private final MetaManifest<CustomPropertiesRecord> meta;
    private final BHiveExecution hive;

    public CustomProperties(Manifest.Key manifestKey, BHiveExecution hive) {
        this.hive = hive;
        this.meta = new MetaManifest<>(manifestKey, false, CustomPropertiesRecord.class);
    }

    public void set(CustomPropertiesRecord record) {
        store(record); // overwrite
    }

    public CustomPropertiesRecord read() {
        return readOrCreate();
    }

    public MetaManifest<CustomPropertiesRecord> getMetaManifest() {
        return meta;
    }

    private CustomPropertiesRecord readOrCreate() {
        CustomPropertiesRecord stored = meta.read(hive);
        if (stored == null) {
            return new CustomPropertiesRecord();
        }
        return stored;
    }

    private void store(CustomPropertiesRecord record) {
        meta.write(hive, record);
    }

}
