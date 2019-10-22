package io.bdeploy.ui;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;

/**
 * {@link MetaManifest} which keeps track of which attached local server is responsible for a certain instance.
 */
public class LocalMasterAssociationMetaManifest {

    private String masterName;

    public String getMasterName() {
        return masterName;
    }

    public static void associate(BHive hive, Manifest.Key key, String master) {
        MetaManifest<LocalMasterAssociationMetaManifest> mm = new MetaManifest<>(key, false,
                LocalMasterAssociationMetaManifest.class);

        LocalMasterAssociationMetaManifest value = new LocalMasterAssociationMetaManifest();
        value.masterName = master;
        mm.write(hive, value);
    }

    public static String read(BHive hive, Manifest.Key key) {
        MetaManifest<LocalMasterAssociationMetaManifest> mm = new MetaManifest<>(key, false,
                LocalMasterAssociationMetaManifest.class);

        LocalMasterAssociationMetaManifest value = mm.read(hive);
        if (value == null) {
            return null;
        }
        return value.masterName;
    }

}
