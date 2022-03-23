package io.bdeploy.interfaces.manifest.attributes;

import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;

public class CustomAttributes {

    private final MetaManifest<CustomAttributesRecord> meta;
    private final BHiveExecution hive;

    public CustomAttributes(Manifest.Key manifestKey, BHiveExecution hive) {
        this.hive = hive;
        this.meta = new MetaManifest<>(manifestKey, false, CustomAttributesRecord.class);
    }

    public void set(CustomAttributesRecord rec) {
        store(rec); // overwrite
    }

    public CustomAttributesRecord read() {
        return readOrCreate();
    }

    public MetaManifest<CustomAttributesRecord> getMetaManifest() {
        return meta;
    }

    private CustomAttributesRecord readOrCreate() {
        CustomAttributesRecord stored = meta.read(hive);
        if (stored == null) {
            return new CustomAttributesRecord();
        }
        return stored;
    }

    private void store(CustomAttributesRecord rec) {
        meta.write(hive, rec);
    }

}
