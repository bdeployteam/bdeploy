package io.bdeploy.ui;

import java.time.Instant;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;

/**
 * {@link MetaManifest} which keeps track of which attached managed server is responsible for a certain instance.
 */
public class ManagedMasterAssociationMetaManifest {

    private String masterName;
    private Instant lastSync;

    public String getMasterName() {
        return masterName;
    }

    public Instant getLastSync() {
        return lastSync;
    }

    public static void associate(BHive hive, Manifest.Key key, String master) {
        MetaManifest<ManagedMasterAssociationMetaManifest> mm = new MetaManifest<>(key, false,
                ManagedMasterAssociationMetaManifest.class);

        ManagedMasterAssociationMetaManifest value = new ManagedMasterAssociationMetaManifest();
        value.masterName = master;
        value.lastSync = Instant.now();
        mm.write(hive, value);
    }

    public static ManagedMasterAssociationMetaManifest read(BHive hive, Manifest.Key key) {
        MetaManifest<ManagedMasterAssociationMetaManifest> mm = new MetaManifest<>(key, false,
                ManagedMasterAssociationMetaManifest.class);

        ManagedMasterAssociationMetaManifest value = mm.read(hive);
        if (value == null) {
            return new ManagedMasterAssociationMetaManifest();
        }
        return value;
    }

}
