package io.bdeploy.ui;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;

/**
 * {@link MetaManifest} which keeps track of which attached managed server is responsible for a certain instance.
 */
public class ManagedMasterAssociationMetaManifest {

    private String masterName;

    public String getMasterName() {
        return masterName;
    }

    public static void associate(BHive hive, Manifest.Key key, String master) {
        MetaManifest<ManagedMasterAssociationMetaManifest> mm = new MetaManifest<>(key, false,
                ManagedMasterAssociationMetaManifest.class);

        ManagedMasterAssociationMetaManifest value = new ManagedMasterAssociationMetaManifest();
        value.masterName = master;
        mm.write(hive, value);
    }

    public static String read(BHive hive, Manifest.Key key) {
        MetaManifest<ManagedMasterAssociationMetaManifest> mm = new MetaManifest<>(key, false,
                ManagedMasterAssociationMetaManifest.class);

        ManagedMasterAssociationMetaManifest value = mm.read(hive);
        if (value == null) {
            return null;
        }
        return value.masterName;
    }

}
