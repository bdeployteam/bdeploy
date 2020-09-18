package io.bdeploy.interfaces.manifest.banner;

import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;

public class InstanceBanner {

    private final MetaManifest<InstanceBannerRecord> meta;
    private final BHiveExecution hive;

    public InstanceBanner(Manifest.Key instanceManifest, BHiveExecution hive) {
        this.hive = hive;
        this.meta = new MetaManifest<>(instanceManifest, false, InstanceBannerRecord.class);
    }

    public void set(InstanceBannerRecord banner) {
        store(banner); // overwrite
    }

    public InstanceBannerRecord read() {
        return readOrCreate();
    }

    private InstanceBannerRecord readOrCreate() {
        InstanceBannerRecord stored = meta.read(hive);
        if (stored == null) {
            return new InstanceBannerRecord();
        }
        return stored;
    }

    private void store(InstanceBannerRecord record) {
        meta.write(hive, record);
    }

}
